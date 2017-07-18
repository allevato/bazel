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

package com.google.devtools.skylark.debugger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/**
 * Represents a request sent from a debugger client to the debug server.
 */
class DebugRequest {
  private DebugProtos.DebugRequest.Builder requestProtoBuilder;

  private DebugRequest(DebugProtos.DebugRequest.Builder requestProtoBuilder) {
    this.requestProtoBuilder = requestProtoBuilder;
  }

  DebugProtos.DebugRequest asRequestProto(long sequenceNumber) {
    return requestProtoBuilder.setSequenceNumber(sequenceNumber).build();
  }

  static DebugRequest listThreadsRequest() {
    return new DebugRequest(DebugProtos.DebugRequest.newBuilder()
        .setListThreads(DebugProtos.ListThreadsRequest.getDefaultInstance()));
  }

  static DebugRequest setBreakpointsRequest(Iterable<Breakpoint> breakpoints) {
    Iterable<DebugProtos.Breakpoint> breakpointProtos = Iterables.transform(
        breakpoints, (Breakpoint breakpoint) -> {
          return breakpoint.asBreakpointProto();
        });

    return new DebugRequest(DebugProtos.DebugRequest.newBuilder()
        .setSetBreakpoints(DebugProtos.SetBreakpointsRequest.newBuilder()
            .addAllBreakpoint(ImmutableList.copyOf(breakpointProtos))));
  }

  static DebugRequest continueExecutionRequest(long threadId, DebugProtos.Stepping stepping) {
    return new DebugRequest(DebugProtos.DebugRequest.newBuilder()
        .setContinueExecution(DebugProtos.ContinueExecutionRequest.newBuilder()
            .setThreadId(threadId)
            .setStepping(stepping)));
  }

  static DebugRequest evaluateRequest(long threadId, String expression) {
    return new DebugRequest(DebugProtos.DebugRequest.newBuilder()
        .setEvaluate(DebugProtos.EvaluateRequest.newBuilder()
            .setThreadId(threadId)
            .setExpression(expression)));
  }

  static DebugRequest listFramesRequest(long threadId) {
    return new DebugRequest(DebugProtos.DebugRequest.newBuilder()
        .setListFrames(DebugProtos.ListFramesRequest.newBuilder()
            .setThreadId(threadId)));
  }
}
