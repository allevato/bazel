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

import static com.google.devtools.common.options.OptionDocumentationCategory.UNCATEGORIZED;
import static com.google.devtools.common.options.proto.OptionFilters.OptionEffectTag.NO_OP;

import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;

/** Command line options supported by the basic Skylark debugger. */
public class BasicDebuggerOptions extends OptionsBase {

  @Option(
      name = "host",
      defaultValue = "localhost",
      category = "connection",
      documentationCategory = UNCATEGORIZED,
      effectTags = NO_OP,
      help = "The host on which the Skylark debug server is running."
  )
  public String host;

  @Option(
      name = "port",
      defaultValue = "7300",
      category = "connection",
      documentationCategory = UNCATEGORIZED,
      effectTags = NO_OP,
      help = "The port on which the Skylark debug server is listening for connections."
  )
  public int port;
}
