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
import com.google.devtools.build.lib.syntax.debugprotocol.DebugEvent;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/** Manages the network socket and debugging state for threads running Skylark code. */
public class SkylarkDebugServer {

  private static SkylarkDebugServer instance;

  public synchronized static SkylarkDebugServer getInstance() {
    if (instance == null) {
      instance = new SkylarkDebugServer();
    }
    return instance;
  }

  /** The server socket for the debug server. */
  private ServerSocket serverSocket;

  /** The input stream used to read requests from the currently connected client. */
  private InputStream requestStream;

  /** The output stream used to write events to the currently connected client. */
  private OutputStream eventStream;

  /**
   * Opens the debug server socket and spawns a thread that listens for an incoming connection.
   *
   * This method returns immediately.
   *
   * @param port the port on which the server should listen for connections
   * @throws IOException if an I/O error occurs while opening the socket
   */
  public void open(int port) throws IOException {
    serverSocket = new ServerSocket(port);

    new Thread(() -> {
      try {
        listenForIncomingConnections();
      } catch (IOException e) {
        // TODO(allevato): Do some more appropriate error handling here.
        System.err.println("Debug server shut down due to exception: " + e.getMessage());
        e.printStackTrace();
      }
    }).start();
  }

  /**
   * Closes the debug server's socket.
   *
   * @throws IOException if an I/O error occurs while closing the socket
   */
  public void close() throws IOException {
    serverSocket.close();
  }

  /**
   * Listens for incoming connections from clients and accepts them only if no other client is
   * currently attached to the debug server.
   *
   * @throws IOException if an I/O error occurs while listening for the connection
   */
  private void listenForIncomingConnections() throws IOException {
    while (!serverSocket.isClosed()) {
      // TODO(allevato): Only allow a single client to be connected. This will require some small
      // amount of handshaking to properly handle the case where a client terminates abnormally and
      // doesn't gracefully shut down the connection.
      final Socket clientSocket = serverSocket.accept();

      requestStream = clientSocket.getInputStream();
      eventStream = clientSocket.getOutputStream();

      boolean running = true;
      while (running) {
        running = handleClientRequest();
      }

      clientSocket.close();
    }
  }

  /** Reads a request from the client, handles it, and writes the response back out. */
  private boolean handleClientRequest() throws IOException {
    DebugProtos.DebugRequest request = DebugProtos.DebugRequest.parseDelimitedFrom(requestStream);
    long sequenceNumber = request.getSequenceNumber();

    DebugEvent response = null;
    boolean keepRunning = true;

    switch (request.getPayloadCase()) {
      case LISTTHREADS:
        response = handleListThreadsRequest(sequenceNumber, request.getListThreads());
        break;
      default:
        // TODO(allevato): Return an error response.
        keepRunning = false;
        break;
    }

    if (response != null) {
      response.asEventProto().writeDelimitedTo(eventStream);
      eventStream.flush();
    }

    return keepRunning;
  }

  /** Handles a {@code ListThreadsRequest} and returns its response. */
  private DebugEvent handleListThreadsRequest(
      long sequenceNumber, DebugProtos.ListThreadsRequest listThreads) throws IOException {
    return DebugEvent.listThreadsResponse(sequenceNumber, ImmutableList.of());
  }
}
