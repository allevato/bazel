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

import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/** Represents a breakpoint maintained by the client and sent to the server. */
class Breakpoint {
  private DebugProtos.Breakpoint.Builder breakpointBuilder;

  private Breakpoint(DebugProtos.Breakpoint.Builder breakpointBuilder) {
    this.breakpointBuilder = breakpointBuilder;
  }

  public DebugProtos.Breakpoint asBreakpointProto() {
    return breakpointBuilder.build();
  }

  public static Breakpoint locationBreakpoint(String path, int lineNumber) {
    return new Breakpoint(DebugProtos.Breakpoint.newBuilder()
        .setLocation(DebugProtos.Location.newBuilder()
            .setPath(path)
            .setLineNumber(lineNumber)
            .build()));
  }

  public boolean equals(Object other) {
    if (!(other instanceof Breakpoint)) {
      return false;
    }
    Breakpoint otherBreakpoint = (Breakpoint) other;
    return asBreakpointProto().equals(otherBreakpoint.asBreakpointProto());
  }

  public int hashCode() {
    return asBreakpointProto().hashCode();
  }
}
