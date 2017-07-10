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
import com.google.devtools.common.options.OptionsParser;
import com.google.protobuf.TextFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** A basic terminal-based debugger for Skylark code. */
class BasicDebugger {

  private static final String PROMPT = "debugger> ";

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

  private BasicDebugger(BasicDebuggerOptions options) {
    this.options = options;
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

  /** Provide a REPL for accessing debugger commands. */
  public void commandLoop() {
    long sequenceNumber = 1;

    String input;
    while ((input = prompt()) != null) {
      try {
        DebugRequest request = (DebugRequest) BuildFileAST.eval(env, input);
        TextFormat.print(request.asRequestProto(sequenceNumber++), System.out);
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

      new BasicDebugger(options).commandLoop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
