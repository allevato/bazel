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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

/** Manages the network socket and debugging state for threads running Skylark code. */
public class SkylarkDebugServer {

  private static SkylarkDebugServer instance;

  public synchronized static SkylarkDebugServer getInstance() {
    if (instance == null) {
      instance = new SkylarkDebugServer();
    }
    return instance;
  }

  private ServerSocket socket;

  private InputStream requestStream;

  private OutputStream eventStream;

  private SkylarkDebugServer() {
  }

  /**
   * Opens the debug server socket and spawns a thread that listens for an incoming connection.
   *
   * This method returns immediately.
   *
   * @param port the port on which the server should listen for connections
   * @throws IOException if an I/O error occurs while opening the socket
   */
  public void open(int port) throws IOException {
    socket = new ServerSocket(port);

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          listenForIncomingConnections();
        } catch (IOException e) {
          // TODO: ???
          System.err.println("Debug server shut down due to exception: " + e.getMessage());
          e.printStackTrace();
        }
      }
    }).start();
  }

  public void close() throws IOException {
    socket.close();
  }
}
