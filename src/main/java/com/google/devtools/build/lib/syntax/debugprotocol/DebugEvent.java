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

  public static DebugEvent listThreadsResponse(
      long sequenceNumber, List<DebugProtos.Thread> threads) {
    return new DebugEvent(DebugProtos.DebugEvent.newBuilder()
        .setSequenceNumber(sequenceNumber)
        .setListThreads(DebugProtos.ListThreadsResponse.newBuilder()
            .addAllThread(threads))
        .build());
  }
}
