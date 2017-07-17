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

import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.events.EventKind;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.Mutability;
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

  private static final String PROMPT = "debugger> ";

  private static final int CONNECTION_ATTEMPTS = 10;

  private static final EventHandler PRINT_HANDLER =
      new EventHandler() {
        @Override
        public void handle(Event event) {
          if (event.getKind() == EventKind.ERROR) {
            System.err.println(event.getMessage());
          } else {
            System.out.println(event.getMessage());
          }
        }
      };

  private final BufferedReader reader =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

  private final Mutability mutability = Mutability.create("debugger");

  private final Environment env =
      Environment.builder(mutability)
          .setGlobals(BasicDebuggerFunctions.createGlobals())
          .setEventHandler(PRINT_HANDLER)
          .build();

  private BasicDebuggerOptions options;

  private Socket socket;

  private InputStream eventStream;

  private OutputStream requestStream;

  private Semaphore responseLatch;

  private BasicDebugger(BasicDebuggerOptions options) {
    this.options = options;
    this.responseLatch = new Semaphore(0);
  }

  private String prompt() {
    System.out.print(PROMPT);
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
        case THREADSTARTED:
          handleThreadStartedEvent(eventProto.getThreadStarted());
          break;
        case THREADENDED:
          handleThreadEndedEvent(eventProto.getThreadEnded());
          break;
        default:
          System.out.println("Unknown event received from server:");
          TextFormat.print(eventProto, System.out);
          break;
      }

      // TODO(allevato): Handle an exit message.

      if (eventProto.getSequenceNumber() != 0) {
        responseLatch.release();
      }
    }
  }

  private void handleError(DebugProtos.Error error) {
    System.out.println("\nERROR: " + error.getMessage());
  }

  private void handleListThreadsResponse(DebugProtos.ListThreadsResponse listThreads)
      throws IOException {
    System.out.println("\nCurrent threads:");
    TextFormat.print(listThreads, System.out);
  }

  private void handleEvaluateResponse(DebugProtos.EvaluateResponse evaluate) throws IOException {
    System.out.println("\nResult:");
    System.out.println(evaluate.getResult());
  }

  private void handleThreadStartedEvent(DebugProtos.ThreadStartedEvent threadStarted)
      throws IOException {
    System.out.printf("\n[Thread %d has started]\n", threadStarted.getThread().getId());
  }

  private void handleThreadEndedEvent(DebugProtos.ThreadEndedEvent threadEnded) throws IOException {
    System.out.printf("\n[Thread %d has ended]\n", threadEnded.getThread().getId());
  }

  /** Provide a REPL for accessing debugger commands. */
  public void commandLoop() {
    long sequenceNumber = 1;

    String input;
    while ((input = prompt()) != null) {
      try {
        DebugRequest request = (DebugRequest) BuildFileAST.eval(env, input);
        DebugProtos.DebugRequest requestProto = request.asRequestProto(sequenceNumber++);
        requestProto.writeDelimitedTo(requestStream);
        requestStream.flush();

        responseLatch.acquireUninterruptibly();
      } catch (Exception e) {
        e.printStackTrace();
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
