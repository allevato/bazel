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
import com.google.devtools.build.lib.skylarkinterface.Param;
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

  @SkylarkSignature(
      name = "set_line_breakpoint",
      doc = "Sets a breakpoint at a line in a Skylark source file.",
      parameters = {
          @Param(
              name = "path",
              type = String.class,
              doc = "The path to the Skylark source file."
          ),
          @Param(
              name = "line_number",
              type = Integer.class,
              doc = "The line number in the Skylark source file."
          )
      },
      returnType = DebugRequest.class,
      useEnvironment = true
  )
  private static final BuiltinFunction setLineBreakpoint =
      new BuiltinFunction("set_line_breakpoint") {
        public DebugRequest invoke(String path, Integer lineNumber, Environment env)
            throws EvalException {
          Breakpoint breakpoint = Breakpoint.locationBreakpoint(path, lineNumber);
          BasicDebuggerState state = getState(env);
          state.breakpoints.add(breakpoint);
          return DebugRequest.setBreakpointsRequest(state.breakpoints);
        }
      };

  @SkylarkSignature(
      name = "go",
      doc = "Continues execution of a paused thread.",
      parameters = {
          @Param(
              name = "thread",
              type = Integer.class,
              doc = "The identifier of the thread to continue."
          )
      },
      returnType = DebugRequest.class
  )
  private static final BuiltinFunction go =
      new BuiltinFunction("go") {
        public DebugRequest invoke(Integer threadId)
            throws EvalException {
          // TODO(allevato): Make the thread identifier optional once the current thread is
          // tracked.
          return DebugRequest.continueExecutionRequest(threadId);
        }
      };

  @SkylarkSignature(
      name = "eval",
      doc = "Evaluates an expression in the context of a thread.",
      parameters = {
          @Param(
              name = "expression",
              type = String.class,
              doc = "The expression to evaluate."
          ),
          @Param(
              name = "thread",
              type = Integer.class,
              doc = "The identifier of the thread to evaluate in.",
              positional = false
          )
      },
      returnType = DebugRequest.class
  )
  private static final BuiltinFunction eval =
      new BuiltinFunction("eval") {
        public DebugRequest invoke(String expression, Integer threadId)
            throws EvalException {
          // TODO(allevato): Make the thread identifier optional once the current thread is
          // tracked.
          return DebugRequest.evaluateRequest(threadId, expression);
        }
      };

  @SkylarkSignature(
      name = "list_frames",
      doc = "Lists the stack frames of a thread.",
      parameters = {
          @Param(
              name = "thread",
              type = Integer.class,
              doc = "The identifier of the thread whose frames should be listed."
          )
      },
      returnType = DebugRequest.class
  )
  private static final BuiltinFunction listFrames =
      new BuiltinFunction("list_frames") {
        public DebugRequest invoke(Integer threadId)
            throws EvalException {
          // TODO(allevato): Make the thread identifier optional once the current thread is
          // tracked.
          return DebugRequest.listFramesRequest(threadId);
        }
      };

  /** Returns the debug client's mutable state. */
  private static BasicDebuggerState getState(Environment env) {
    return (BasicDebuggerState) env.lookup("_state");
  }

  /** Creates the global commands available in the debugger. */
  static Environment.Frame createGlobals() {
    try (Mutability mutability = Mutability.create("DEBUGGER")) {
      Environment env = Environment.builder(mutability).build();
      for (BaseFunction function : debuggerFunctions) {
        env.setup(function.getName(), function);
      }
      env.setup("_state", new BasicDebuggerState());
      return env.getGlobals();
    }
  }

  static final List<BaseFunction> debuggerFunctions =
      ImmutableList.<BaseFunction>of(listThreads, setLineBreakpoint, go, eval, listFrames);

  static {
    SkylarkSignatureProcessor.configureSkylarkFunctions(BasicDebuggerFunctions.class);
  }
}
