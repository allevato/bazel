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
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugEvent;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

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

  /** Tracks the currently active threads. */
  private ConcurrentHashMap<Long, Object> requestHandlers;

  public SkylarkDebugServer() {
    requestHandlers = new ConcurrentHashMap<>();
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
   * Tracks the execution of the given callable object in the debug server.
   *
   * @param env the Skylark execution environment
   * @param callable the callable object whose execution will be tracked
   * @param <T> the result type of the callable
   * @return the value returned by the callable
   * @throws EvalException if the callable throws an exception
   */
  public <T> T runWithDebugging(Environment env, SkylarkDebugCallable<T> callable)
      throws EvalException, InterruptedException {
    long threadId = Thread.currentThread().getId();
    // TODO(allevato): Associate a debug adapter with the environment and put that here.
    requestHandlers.put(threadId, new Object());
    postEvent(DebugEvent.threadStartedEvent(
        DebugProtos.Thread.newBuilder().setId(threadId).build()));

    T result = callable.call();

    postEvent(DebugEvent.threadEndedEvent(
        DebugProtos.Thread.newBuilder().setId(threadId).build()));
    requestHandlers.remove(threadId);

    return result;
  }

  /**
   * Posts a debug event if a client is currently connected.
   *
   * @param event the event to post
   */
  private void postEvent(DebugEvent event) {
    // TODO(allevato): Synchronize this.
    if (eventStream != null) {
      try {
        event.asEventProto().writeDelimitedTo(eventStream);
      } catch (IOException e) {
        System.err.println("Failed to post event: " + e.getMessage());
      }
    }
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

      Thread clientThread = new Thread(() -> {
        try {
          requestStream = clientSocket.getInputStream();
          eventStream = clientSocket.getOutputStream();

          boolean running = true;
          while (running) {
            running = handleClientRequest();
          }

          clientSocket.close();
        }
        catch (IOException e) {
          System.err.println("Client thread died because of an I/O error: " + e.getMessage());
          e.printStackTrace();
        }
      });
      clientThread.setDaemon(true);
      clientThread.start();
    }
  }

  /** Reads a request from the client, handles it, and writes the response back out. */
  private boolean handleClientRequest() throws IOException {
    DebugProtos.DebugRequest request = DebugProtos.DebugRequest.parseDelimitedFrom(requestStream);
    if (request == null) {
      // This can happen if the client drops the connection, so terminate the thread.
      return false;
    }

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
