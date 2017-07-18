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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.devtools.build.lib.syntax.ClassObject;
import com.google.devtools.build.lib.syntax.EvalUtils;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import java.util.List;
import java.util.Map;

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

  /** Returns a {@code Value} proto builder containing the debugger representation of a value. */
  private static DebugProtos.Value.Builder makeValueBuilder(Object value) {
    Class<?> type = value.getClass();
    DebugProtos.Value.Builder builder = DebugProtos.Value.newBuilder()
        .setType(EvalUtils.getDataTypeName(value));

    if (type == String.class) {
      String stringValue = (String) value;
      return builder.setDescription(stringValue);
    }
    if (Number.class.isAssignableFrom(type)) {
      Number numberValue = (Number) value;
      return builder.setDescription(numberValue.toString());
    }
    if (List.class.isAssignableFrom(type)) {
      List<?> listValue = (List<?>) value;
      return buildListValue(builder, listValue);
    }
    if (Map.class.isAssignableFrom(type)) {
      Map<?, ?> dictValue = (Map<?, ?>) value;
      return buildDictValue(builder, dictValue);
    }
    if (ClassObject.class.isAssignableFrom(type)) {
      ClassObject structValue = (ClassObject) value;
      return buildStructValue(builder, structValue);
    }
    return builder.setDescription(value.toString());
  }

  private static DebugProtos.Value.Builder buildListValue(
      DebugProtos.Value.Builder builder, List<?> listValue) {
    for (int i = 0; i < listValue.size(); ++i) {
      Object elementValue = listValue.get(i);
      String indexLabel = String.format("[%d]", i);
      DebugValueMirror childMirror = new DebugValueMirror(elementValue);
      builder.addChild(childMirror.asValueProto(indexLabel));
    }
    return builder;
  }

  private static DebugProtos.Value.Builder buildStructValue(
      DebugProtos.Value.Builder builder, ClassObject structValue) {
    ImmutableList<String> keys = Ordering.natural().immutableSortedCopy(structValue.getKeys());
    for (String key : keys) {
      Object fieldValue = structValue.getValue(key);
      DebugValueMirror childMirror = new DebugValueMirror(fieldValue);
      builder.addChild(childMirror.asValueProto(key));
    }
    return builder;
  }

  private static DebugProtos.Value.Builder buildDictValue(
      DebugProtos.Value.Builder builder, Map<?, ?> dictValue) {
    for (Map.Entry<?, ?> entry : dictValue.entrySet()) {
      DebugProtos.Value entryKey =
          new DebugValueMirror(entry.getKey()).asValueProto("key");
      DebugProtos.Value entryValue =
          new DebugValueMirror(entry.getValue()).asValueProto("value");

      DebugProtos.Value.Builder entryBuilder = DebugProtos.Value.newBuilder()
          .setLabel("(entry)")
          .addChild(entryKey)
          .addChild(entryValue);
      builder.addChild(entryBuilder);
    }
    return builder;
  }
}
