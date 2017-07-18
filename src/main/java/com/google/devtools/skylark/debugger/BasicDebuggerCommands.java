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

/** Commands supported by the basic debugger. */
class BasicDebuggerCommands {

  private static final Command listThreads = new Command("threads", "t") {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      return DebugRequest.listThreadsRequest();
    }
  };

  private static final Command setLineBreakpoint = new Command("b") {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      String path = scanner.nextPath();
      int lineNumber = scanner.nextInt();

      Breakpoint breakpoint = Breakpoint.locationBreakpoint(path, lineNumber);
      state.addBreakpoint(breakpoint);
      return DebugRequest.setBreakpointsRequest(state.getBreakpoints());
    }
  };

  private static final Command go = new Command("go", "g") {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      long threadId = scanner.optionalNextLong(state.getCurrentThread());
      if (threadId == 0) {
        throw new IllegalStateException("Not on a Skylark thread");
      }
      return DebugRequest.continueExecutionRequest(threadId);
    }
  };

  private static final Command print = new Command("print", "p") {
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

  private static final Command listFrames = new Command("frames", "f") {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      long threadId = scanner.optionalNextLong(state.getCurrentThread());
      if (threadId == 0) {
        throw new IllegalStateException("Not on a Skylark thread");
      }
      return DebugRequest.listFramesRequest(threadId);
    }
  };

  private static final Command setThread = new Command("setthread", "st") {
    @Override
    public DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state) {
      // TODO(allevato): Disallow threads that don't exist.
      long threadId = scanner.nextLong();
      state.setCurrentThread(threadId);
      return null;
    }
  };

  static final ImmutableList<Command> COMMAND_LIST = ImmutableList.of(
      print,
      go,
      listFrames,
      listThreads,
      setLineBreakpoint,
      setThread
  );
}
