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

package com.google.devtools.build.lib.syntax.debugprotocol;

import java.util.List;

/** Represents an event or response sent from the debug server to a debugger client. */
public class DebugEvent {
  private DebugProtos.DebugEvent eventProto;

  private DebugEvent(DebugProtos.DebugEvent eventProto) {
    this.eventProto = eventProto;
  }

  public DebugProtos.DebugEvent asEventProto() {
    return eventProto;
  }

  public static DebugEvent error(long sequenceNumber, String message) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setError(DebugProtos.Error.newBuilder()
            .setMessage(message))
        .build());
  }

  public static DebugEvent listThreadsResponse(
      long sequenceNumber, List<DebugProtos.Thread> threads) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setListThreads(DebugProtos.ListThreadsResponse.newBuilder()
            .addAllThread(threads))
        .build());
  }

  public static DebugEvent setBreakpointsResponse(long sequenceNumber) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setSetBreakpoints(DebugProtos.SetBreakpointsResponse.getDefaultInstance())
        .build());
  }

  public static DebugEvent continueExecutionResponse(long sequenceNumber) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setContinueExecution(DebugProtos.ContinueExecutionResponse.getDefaultInstance())
        .build());
  }

  public static DebugEvent evaluateResponse(long sequenceNumber, DebugProtos.Value result) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setEvaluate(DebugProtos.EvaluateResponse.newBuilder()
            .setResult(result))
        .build());
  }

  public static DebugEvent listFramesResponse(
      long sequenceNumber, Iterable<DebugProtos.Frame> frames) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setListFrames(DebugProtos.ListFramesResponse.newBuilder()
            .addAllFrame(frames))
        .build());
  }

  public static DebugEvent threadStartedEvent(DebugProtos.Thread thread) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setThreadStarted(DebugProtos.ThreadStartedEvent.newBuilder()
            .setThread(thread))
        .build());
  }

  public static DebugEvent threadEndedEvent(DebugProtos.Thread thread) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setThreadEnded(DebugProtos.ThreadEndedEvent.newBuilder()
            .setThread(thread))
        .build());
  }

  public static DebugEvent threadPausedEvent(DebugProtos.Thread thread) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setThreadPaused(DebugProtos.ThreadPausedEvent.newBuilder()
            .setThread(thread))
        .build());
  }

  public static DebugEvent threadContinuedEvent(DebugProtos.Thread thread) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setThreadContinued(DebugProtos.ThreadContinuedEvent.newBuilder()
            .setThread(thread))
        .build());
  }
}
