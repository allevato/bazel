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

import com.google.devtools.build.lib.debugging.SkylarkDebugReflectables;
import com.google.devtools.build.lib.debugging.SkylarkDebugView.Child;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.ASTNode;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/** Utility functions for Skylark debugging. */
public class DebugUtils {

  /** Returns a {@code Location} proto with the location of the given AST node. */
  public static DebugProtos.Location getLocationProto(ASTNode node) {
    Location location = node.getLocation();
    if (location == null) {
      return null;
    }
    return getLocationProto(location);
  }

  /** Returns a {@code Location} proto corresponding to the given location. */
  public static DebugProtos.Location getLocationProto(Location location) {
    Location.LineAndColumn lineAndColumn = location.getStartLineAndColumn();
    if (lineAndColumn == null) {
      return null;
    }
    try {
      // TODO(allevato): Should this be using Bazel's VFS? We'd need to make that pluggable.
      Path path = FileSystems.getDefault().getPath(location.getPath().getPathString());
      return DebugProtos.Location.newBuilder()
          .setPath(path.toRealPath().toString())
          .setLineNumber(lineAndColumn.getLine())
          .build();
    } catch (IOException e) {
      return null;
    }
  }

  /** Returns a {@code Value} proto builder containing the debugger representation of a value. */
  public static DebugProtos.Value getValueProto(String label, Object value) {
    DebugProtos.Value.Builder builder = DebugProtos.Value.newBuilder()
        .setLabel(label)
        .setType(EvalUtils.getDataTypeName(value))
        .setDescription(Printer.repr(value));

    for (Child child : SkylarkDebugReflectables.getDebugViewChildren(value)) {
      builder.addChild(getValueProto(child.getLabel(), child.getValue()));
    }

    return builder.build();
  }
}
