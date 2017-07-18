// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.skylark.debugger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/** Commands supported by the basic debugger. */
class BasicDebuggerCommands {

  private static final String LIST_THREADS_DOC = "Lists the currently running threads";
  private static final String SET_LINE_BREAKPOINT_DOC = "Sets a breakpoint on a line";
  private static final String GO_DOC = "Continues execution of a paused thread";
  private static final String PRINT_DOC = "Evaluates and prints a Skylark expression";
  private static final String LIST_FRAMES_DOC = "Lists the stack frames of a thread";
  private static final String SET_THREAD_DOC = "Switches the debugger to a different thread";
  private static final String STEP_INTO_DOC = "Steps into the next function";
  private static final String STEP_OUT_DOC = "Steps out of the current function";
  private static final String STEP_OVER_DOC = "Steps over the next statement";
  private static final String QUIT_DOC = "Exits the debugger";
  private static final String HELP_DOC = "Displays debugger help";

  /** Wraps the common functionality of commands that send {@code ContinueExecutionRequest}s. */
  private static class ContinueExecutionCommand extends Command {
    private final DebugProtos.Stepping stepping;

    ContinueExecutionCommand(
        String shortName, String longName, String docString, DebugProtos.Stepping stepping) {
      super(shortName, longName, docString);
      this.stepping = stepping;
    }

    @Override
    public final DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      long threadId = scanner.optionalNextLong(state.getCurrentThread());
      if (threadId == 0) {
        throw new IllegalStateException("Not on a Skylark thread");
      }
      return DebugRequest.continueExecutionRequest(threadId, stepping);
    }
  }

  private static final Command listThreads =
      new Command("threads", "t", LIST_THREADS_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      return DebugRequest.listThreadsRequest();
    }
  };

  private static final Command setLineBreakpoint =
      new Command("setbreakpoint", "b", SET_LINE_BREAKPOINT_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      String path = scanner.nextPath();
      int lineNumber = scanner.nextInt();

      Breakpoint breakpoint = Breakpoint.locationBreakpoint(path, lineNumber);
      state.addBreakpoint(breakpoint);
      return DebugRequest.setBreakpointsRequest(state.getBreakpoints());
    }
  };

  private static final Command go = new ContinueExecutionCommand(
      "go", "g", GO_DOC, DebugProtos.Stepping.NONE);

  private static final Command stepOver = new ContinueExecutionCommand(
      "stepover", "s", STEP_OVER_DOC, DebugProtos.Stepping.OVER);

  private static final Command stepInto = new ContinueExecutionCommand(
      "stepin", "si", STEP_INTO_DOC, DebugProtos.Stepping.INTO);

  private static final Command stepOut = new ContinueExecutionCommand(
      "stepout", "so", STEP_OUT_DOC, DebugProtos.Stepping.OUT);

  private static final Command print =
      new Command("print", "p", PRINT_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      String expression = scanner.nextString();
      long threadId = scanner.optionalNextLong(state.getCurrentThread());
      if (threadId == 0) {
        throw new IllegalStateException("Not on a Skylark thread");
      }

      return DebugRequest.evaluateRequest(threadId, expression);
    }
  };

  private static final Command listFrames =
      new Command("frames", "f", LIST_FRAMES_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      long threadId = scanner.optionalNextLong(state.getCurrentThread());
      if (threadId == 0) {
        throw new IllegalStateException("Not on a Skylark thread");
      }
      return DebugRequest.listFramesRequest(threadId);
    }
  };

  private static final Command setThread =
      new Command("setthread", "st", SET_THREAD_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      // TODO(allevato): Disallow threads that don't exist.
      long threadId = scanner.nextLong();
      state.setCurrentThread(threadId);
      return null;
    }
  };

  private static final Command quit =
      new Command("quit", "q", QUIT_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      System.out.println("Shutting down.");
      state.exitAtNextOpportunity();
      // TODO(allevato): Send a request that allows the debug server to clean up; for example,
      // clear out breakpoints and continue execution of any paused threads.
      return null;
    }
  };

  private static final Command help =
      new Command("help", "h", HELP_DOC) {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      String commandName = scanner.optionalNextString(null);
      if (commandName == null) {
        printAllCommandsHelp();
      } else {
        Command command = findCommand(commandName);
        if (command != null) {
          printCommandHelp(command);
        } else {
          System.out.printf("There is no command '%s'.\n\n", commandName);
        }
      }
      return null;
    }
  };

  /** Prints the summary help for all commands. */
  private static void printAllCommandsHelp() {
    Iterable<Command> sortedCommands = Ordering.from((Command lhs, Command rhs) -> {
      return lhs.getShortName().compareTo(rhs.getShortName());
    }).sortedCopy(COMMAND_LIST);

    System.out.println("Available commands:\n");

    for (Command command : sortedCommands) {
      System.out.printf("  %-25s  %s\n", command.getShortName() + ", " + command.getLongName(),
          command.getDocString());
    }

    System.out.println("\nType 'help [command]' for more help on a command.\n");
  }

  /** Prints detailed help about a specific command. */
  private static void printCommandHelp(Command command) {
    // TODO(allevato): Implement this.
    System.out.println("Command-specific help isn't implemented yet.\n");
  }

  private static final ImmutableList<Command> COMMAND_LIST = ImmutableList.of(
      go,
      help,
      listFrames,
      listThreads,
      print,
      setLineBreakpoint,
      setThread,
      stepInto,
      stepOut,
      stepOver,
      quit
  );

  private static final ImmutableMap<String, Command> COMMAND_MAP;

  static {
    ImmutableMap.Builder<String, Command> builder = ImmutableMap.builder();
    for (Command command : BasicDebuggerCommands.COMMAND_LIST) {
      builder.put(command.getLongName(), command);
      builder.put(command.getShortName(), command);
    }
    COMMAND_MAP = builder.build();
  }

  /** Returns the command with the given name, or null if there is none. */
  static Command findCommand(String commandName) {
    return COMMAND_MAP.get(commandName);
  }
}
