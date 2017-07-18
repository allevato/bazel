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

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * A wrapper around {@link java.util.Scanner} that provides a better interface for scanning
 * arguments of a debugger command.
 *
 * The methods in this class throw {@code IllegalArgumentException} if the desired argument is not
 * found, meaning that it can be called without each call site being surrounded by {@code try/catch}
 * and those exceptions will bubble up to the command handling loop easily.
 */
class CommandLineScanner {

  private static final Pattern PATH_PATTERN = Pattern.compile("[-_/:.a-zA-Z0-9]+");

  private final Scanner scanner;

  /** Creates a new scanner that scans the given command line. */
  CommandLineScanner(String commandLine) {
    this.scanner = new Scanner(commandLine);
  }

  /** Returns the next path string in the command line string. */
  String nextPath() {
    try {
      return scanner.next(PATH_PATTERN);
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Expected a path");
    }
  }

  /** Returns the next string in the command line string. */
  String nextString() {
    try {
      return scanner.next();
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Expected a string");
    }
  }

  /** Returns the next string in the command line string, or the default value if not present. */
  String optionalNextString(String defaultValue) {
    try {
      return scanner.next();
    } catch (NoSuchElementException e) {
      return defaultValue;
    }
  }

  /** Returns the next integer in the command line string. */
  int nextInt() {
    try {
      return scanner.nextInt();
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Expected an integer");
    }
  }

  /** Returns the next long integer in the command line string. */
  long nextLong() {
    try {
      return scanner.nextLong();
    } catch (NoSuchElementException e) {
      throw new IllegalArgumentException("Expected an integer");
    }
  }

  /**
   * Returns the next long integer in the command line string, or the default value if not present.
   */
  long optionalNextLong(long defaultValue) {
    try {
      return scanner.nextLong();
    } catch (NoSuchElementException e) {
      return defaultValue;
    }
  }
}
