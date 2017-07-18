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

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

/** Manages the debugger's mutable state. */
class BasicDebuggerState {

  private Set<Breakpoint> breakpoints;

  BasicDebuggerState() {
    breakpoints = new HashSet<>();
  }

  /** Gets an immutable copy of the state's current breakpoints. */
  ImmutableSet<Breakpoint> getBreakpoints() {
    return ImmutableSet.copyOf(breakpoints);
  }

  /** Adds a breakpoint to the state. */
  void addBreakpoint(Breakpoint breakpoint) {
    breakpoints.add(breakpoint);
  }
}
