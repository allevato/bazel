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

/** Interface for basic debugger commands. */
abstract class Command {

  private final ImmutableList<String> names;

  /** Creates a new command with the given names. */
  Command(String... names) {
    this.names = ImmutableList.copyOf(names);
  }

  /** Returns the list of names that can be used to invoke this command. */
  ImmutableList<String> getNames() {
    return names;
  }

  /** Executes the command. */
  final DebugRequest execute(CommandLineScanner scanner, BasicDebuggerState state) {
    synchronized (state) {
      return doExecute(scanner, state);
    }
  }

  /**
   * Overridden by subclasses to implement the command logic.
   *
   * When this method is called, it will have an exclusive lock on the {@code state} argument (thus
   * protecting it from changes due to asynchronous events until the command has completed).
   *
   * @param scanner the scanner that the command can use to retrieve arguments
   * @param state the current debugger state, which the command may query and/or modify
   * @return a request to send to the debug server, or null if this command should not send a
   *     request
   */
  protected abstract DebugRequest doExecute(CommandLineScanner scanner, BasicDebuggerState state);
}
