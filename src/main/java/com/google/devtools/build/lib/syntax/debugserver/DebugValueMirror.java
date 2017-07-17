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

import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/** Generates the debugger representation of a Skylark value. */
public class DebugValueMirror {

  private DebugProtos.Value.Builder valueBuilder;

  public DebugValueMirror(Object value) {
    valueBuilder = makeValueBuilder(value);
  }

  /** Returns the {@code Value} proto containing the debugger representation of the value. */
  public DebugProtos.Value asValueProto(String label) {
    if (label != null) {
      valueBuilder.setLabel(label);
    }
    return valueBuilder.build();
  }

  private static DebugProtos.Value.Builder makeValueBuilder(Object value) {
    // TODO(allevato): Handle various types.
    return DebugProtos.Value.newBuilder().setDescription(value.toString());
  }
}
