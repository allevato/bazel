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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import com.google.devtools.common.options.OptionsParser;
import com.google.protobuf.TextFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/** A basic terminal-based debugger for Skylark code. */
class BasicDebugger {

  private static final String LOGO = "\uD83C\uDF3F \uD83D\uDD77 ";

  private static final String PROMPT_WITH_THREAD_FORMAT = LOGO + " on thread %s> ";

  private static final String PROMPT_WITHOUT_THREAD = LOGO + " not on thread> ";

  private static final int CONNECTION_ATTEMPTS = 10;

  private final BufferedReader reader =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

  private final BasicDebuggerOptions options;

  private final BasicDebuggerState debuggerState;

  private ImmutableMap<String, Command> commandMap;

  private Socket socket;

  private InputStream eventStream;

  private OutputStream requestStream;

  private final Semaphore responseLatch;

  private BasicDebugger(BasicDebuggerOptions options) {
    this.options = options;
    this.debuggerState = new BasicDebuggerState();
    this.responseLatch = new Semaphore(0);
  }

  private void printPrompt() {
    synchronized (debuggerState) {
      long threadId = debuggerState.getCurrentThread();
      if (threadId == 0) {
        System.out.print(PROMPT_WITHOUT_THREAD);
      } else {
        String prompt = String.format(PROMPT_WITH_THREAD_FORMAT, threadId);
        System.out.print(prompt);
      }
    }
  }

  private String readCommandLine() {
    try {
      return reader.readLine();
    } catch (IOException io) {
      io.printStackTrace();
      return null;
    }
  }

  private Socket makeConnectionAttempts() {
    int attempt = 1;
    while (attempt <= CONNECTION_ATTEMPTS) {
      System.out.printf("Attempting to connect to %s:%s (%d of %d)... ",
          options.host, options.port, attempt++, CONNECTION_ATTEMPTS);

      try {
        Socket socket = new Socket(options.host, options.port);
        System.out.println("connected.");
        return socket;
      }
      catch (IOException e) {
        System.out.println(e.getMessage());
      }

      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {}
    }

    return null;
  }

  private void open() throws IOException {
    socket = makeConnectionAttempts();
    if (socket == null) {
      System.out.println("ERROR: Could not connect to debug server.");
      System.exit(1);
    }
    eventStream = socket.getInputStream();
    requestStream = socket.getOutputStream();

    Thread thread = new Thread(() -> {
      try {
        listenForEvents();
      } catch (SocketException e) {
        // Ignore.
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    thread.setDaemon(true);
    thread.start();
  }

  private void close() throws IOException {
    socket.close();
  }

  /** Listens for events from the server and processes them as they come in. */
  private void listenForEvents() throws IOException {
    boolean running = true;
    while (running) {
      DebugProtos.DebugEvent eventProto = DebugProtos.DebugEvent.parseDelimitedFrom(eventStream);

      synchronized (debuggerState) {
        switch (eventProto.getPayloadCase()) {
          case ERROR:
            handleError(eventProto.getError());
            break;
          case LISTTHREADS:
            handleListThreadsResponse(eventProto.getListThreads());
            break;
          case SETBREAKPOINTS:
          case CONTINUEEXECUTION:
            // Nothing to do here.
            break;
          case EVALUATE:
            handleEvaluateResponse(eventProto.getEvaluate());
            break;
          case LISTFRAMES:
            handleListFramesResponse(eventProto.getListFrames());
            break;
          case THREADSTARTED:
            handleThreadStartedEvent(eventProto.getThreadStarted());
            break;
          case THREADENDED:
            handleThreadEndedEvent(eventProto.getThreadEnded());
            break;
          case THREADPAUSED:
            handleThreadPausedEvent(eventProto.getThreadPaused());
            break;
          case THREADCONTINUED:
            handleThreadContinuedEvent(eventProto.getThreadContinued());
            break;
          default:
            System.out.println("Unknown event received from server:");
            TextFormat.print(eventProto, System.out);
            break;
        }
      }

      // TODO(allevato): Handle an exit message.

      if (eventProto.getSequenceNumber() != 0) {
        responseLatch.release();
      }

      printPrompt();
    }
  }

  private void handleError(DebugProtos.Error error) {
    System.out.println("\nERROR: " + error.getMessage());
  }

  private void handleListThreadsResponse(DebugProtos.ListThreadsResponse listThreads)
      throws IOException {
    System.out.println("\nCurrent threads:");
    System.out.println("----");
    for (DebugProtos.Thread thread : listThreads.getThreadList()) {
      System.out.printf("%5d: ", thread.getId());
      if (thread.getIsPaused()) {
        DebugProtos.Location location = thread.getLocation();
        System.out.printf("paused at %s:%d\n", location.getPath(), location.getLineNumber());
      } else {
        System.out.println("running");
      }
    }
    System.out.println();
  }

  private void handleEvaluateResponse(DebugProtos.EvaluateResponse evaluate) throws IOException {
    System.out.println("\nResult:");
    printValueProto(0, evaluate.getResult());
  }

  private void handleListFramesResponse(DebugProtos.ListFramesResponse listFrames)
      throws IOException {
    System.out.println("\nCurrent frames:");
    System.out.println("----");
    for (DebugProtos.Frame frame : listFrames.getFrameList()) {
      System.out.println(
          frame.getFunctionName().isEmpty() ? "<global scope>" : frame.getFunctionName());

      for (DebugProtos.Value value : frame.getBindingList()) {
        printValueProto(0, value);
      }

      System.out.println();
    }
  }

  private void handleThreadStartedEvent(DebugProtos.ThreadStartedEvent threadStarted)
      throws IOException {
    long threadId = threadStarted.getThread().getId();
    System.out.printf("\n[Thread %d has started]\n", threadId);

    // If there is no current thread, set it to the new one.
    if (debuggerState.getCurrentThread() == 0) {
      debuggerState.setCurrentThread(threadId);
    }
  }

  private void handleThreadEndedEvent(DebugProtos.ThreadEndedEvent threadEnded) throws IOException {
    long threadId = threadEnded.getThread().getId();
    System.out.printf("\n[Thread %d has ended]\n", threadId);

    // If the current thread is the one that ended, clear it.
    if (debuggerState.getCurrentThread() == threadId) {
      debuggerState.setCurrentThread(0);
    }
  }

  private void handleThreadPausedEvent(DebugProtos.ThreadPausedEvent threadPaused)
      throws IOException {
    DebugProtos.Thread thread = threadPaused.getThread();
    DebugProtos.Location location = thread.getLocation();
    System.out.printf("\n[Thread %d has paused at %s:%s]\n",
        thread.getId(), location.getPath(), location.getLineNumber());
  }

  private void handleThreadContinuedEvent(DebugProtos.ThreadContinuedEvent threadContinued)
      throws IOException {
    System.out.printf("\n[Thread %d has continued]\n", threadContinued.getThread().getId());
  }

  private void printValueProto(int depth, DebugProtos.Value value) {
    for (int i = 0; i < depth; i++) {
      System.out.print("  ");
    }
    if (depth == 0) {
      System.out.printf("  *  ");
    } else {
      System.out.printf("   |- ");
    }

    System.out.printf("%s = %s <%s>\n", value.getLabel(), value.getDescription(), value.getType());

    for (DebugProtos.Value child : value.getChildList()) {
      printValueProto(depth + 1, child);
    }
  }

  private void buildCommandMap() {
    ImmutableMap.Builder<String, Command> builder = ImmutableMap.builder();
    for (Command command : BasicDebuggerCommands.COMMAND_LIST) {
      for (String name : command.getNames()) {
        builder.put(name, command);
      }
    }
    commandMap = builder.build();
  }

  private DebugRequest executeCommand(String commandLine) {
    CommandLineScanner scanner = new CommandLineScanner(commandLine);

    String commandName = null;
    try {
      commandName = scanner.nextString();
    } catch (IllegalArgumentException e) {
      return null;
    }

    Command command = commandMap.get(commandName);
    if (command == null) {
      System.out.println("Unrecognized command: " + commandName);
      return null;
    }

    return command.execute(scanner, debuggerState);
  }

  /** Provide a REPL for accessing debugger commands. */
  private void commandLoop() {
    long sequenceNumber = 1;

    buildCommandMap();
    printPrompt();

    String input;

    while (!debuggerState.shouldExit() && (input = readCommandLine()) != null) {
      try {
        DebugRequest request = executeCommand(input);
        if (request != null) {
          DebugProtos.DebugRequest requestProto = request.asRequestProto(sequenceNumber++);
          requestProto.writeDelimitedTo(requestStream);
          requestStream.flush();

          responseLatch.acquireUninterruptibly();

          // We don't need to print the prompt after a request because the event loop will take care
          // of it when the response comes in.
        } else if (!debuggerState.shouldExit()) {
          printPrompt();
        }
      } catch (Exception e) {
        System.out.println("ERROR: " + e.getMessage() + "\n");
        printPrompt();
      }
    }
  }

  public static void main(String[] args) {
    try {
      OptionsParser parser = OptionsParser.newOptionsParser(BasicDebuggerOptions.class);
      parser.parse(args);
      BasicDebuggerOptions options = parser.getOptions(BasicDebuggerOptions.class);

      BasicDebugger debugger = new BasicDebugger(options);
      debugger.open();
      debugger.commandLoop();
      debugger.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
