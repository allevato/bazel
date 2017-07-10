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
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.SkylarkSignatureProcessor;
import java.util.List;

/**
 * Functions that are added to the Skylark environment to provide the commands used to control the
 * debugger.
 *
 * Each of the functions provided here must return an instance of {@code DebugRequest}. This request
 * is then sent to the server by the main command loop.
 */
class BasicDebuggerFunctions {

  @SkylarkSignature(
      name = "list_threads",
      doc = "Lists the currently active threads that are running Skylark code.",
      returnType = DebugRequest.class
  )
  private static final BuiltinFunction listThreads =
      new BuiltinFunction("list_threads") {
        public DebugRequest invoke() throws EvalException {
          return DebugRequest.listThreadsRequest();
        }
      };

  /** Creates the global commands available in the debugger. */
  static Environment.Frame createGlobals() {
    try (Mutability mutability = Mutability.create("DEBUGGER")) {
      Environment env = Environment.builder(mutability).build();
      for (BaseFunction function : debuggerFunctions) {
        env.setup(function.getName(), function);
      }
      return env.getGlobals();
    }
  }

  static final List<BaseFunction> debuggerFunctions =
      ImmutableList.<BaseFunction>of(listThreads);

  static {
    SkylarkSignatureProcessor.configureSkylarkFunctions(BasicDebuggerFunctions.class);
  }
}
