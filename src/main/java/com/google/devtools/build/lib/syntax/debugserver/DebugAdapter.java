// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.syntax.debugserver;

import com.google.devtools.build.lib.syntax.ASTNode;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/** A bridge that allows the debug server to request information from a Skylark environment. */
public interface DebugAdapter {

  /** Evaluates a Skylark expression in the adapter's environment. */
  Object evaluate(String expression) throws EvalException, InterruptedException;

  /** Returns an iterable of the current stack frames of the adapter's environment. */
  Iterable<DebugProtos.Frame> listFrames(ASTNode ast);

  StepControl stepControl(DebugProtos.Stepping stepping);
}
