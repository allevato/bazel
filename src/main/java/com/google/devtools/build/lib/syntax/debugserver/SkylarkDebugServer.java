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
import com.google.devtools.build.lib.syntax.ASTNode;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugEvent;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Manages the network socket and debugging state for threads running Skylark code. */
public class SkylarkDebugServer {

  /** Information about a paused thread. */
  private class PausedThreadInfo {

    /** The AST node where execution is currently paused. */
    ASTNode astNode;

    PausedThreadInfo(ASTNode astNode) {
      this.astNode = astNode;
    }
  }

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
  private ConcurrentHashMap<Long, DebugAdapter> threadAdapters;

  /** Tracks the objects used to block execution of threads. */
  private ConcurrentHashMap<Long, PausedThreadInfo> pausedThreads;

  private ConcurrentHashMap<Long, StepControl> stepControls;

  /** Breakpoints that are based on location. */
  private Set<DebugProtos.Location> locationBreakpoints;

  /** Lock used to serialize event posting. */
  private Lock postEventLock;

  public SkylarkDebugServer() {
    threadAdapters = new ConcurrentHashMap<>();
    pausedThreads = new ConcurrentHashMap<>();
    stepControls = new ConcurrentHashMap<>();
    locationBreakpoints = new HashSet<>();
    postEventLock = new ReentrantLock();
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
    threadAdapters.put(threadId, env.getDebugAdapter());
    postEvent(DebugEvent.threadStartedEvent(
        DebugProtos.Thread.newBuilder().setId(threadId).build()));

    try {
      return callable.call();
    } finally {
      // TODO(allevato): Communicate whether the exit was successful or not in the event.
      postEvent(DebugEvent.threadEndedEvent(
          DebugProtos.Thread.newBuilder().setId(threadId).build()));
      threadAdapters.remove(threadId);
    }
  }

  /**
   * Pauses the execution of the current thread if there are conditions that should cause it to be
   * paused, such as a breakpoint being reached.
   *
   * @param node the AST node representing the statement or expression currently being executed
   */
  public void pauseIfNecessary(Environment env, ASTNode node) {
    DebugProtos.Location location = DebugUtils.getLocationProto(node);
    if (location == null) {
      return;
    }
    if (locationBreakpoints.contains(location)) {
      pauseCurrentThread(node);
      return;
    }

    StepControl stepControl = stepControls.get(Thread.currentThread().getId());
    if (stepControl != null) {
      if (stepControl.shouldPause(env)) {
        pauseCurrentThread(node);
      }
    }
  }

  /** Returns a {@code Thread} proto builder with information about the given thread. */
  private DebugProtos.Thread.Builder buildThreadProto(long threadId) {
    DebugProtos.Thread.Builder threadBuilder = DebugProtos.Thread.newBuilder()
        .setId(threadId);

    PausedThreadInfo pauseInfo = pausedThreads.get(threadId);
    if (pauseInfo != null) {
      threadBuilder
          .setIsPaused(true)
          .setLocation(DebugUtils.getLocationProto(pauseInfo.astNode));
    }

    return threadBuilder;
  }

  /** Pauses the current thread's execution. */
  private void pauseCurrentThread(ASTNode node) {
    long threadId = Thread.currentThread().getId();
    PausedThreadInfo pauseInfo = new PausedThreadInfo(node);
    pausedThreads.put(threadId, pauseInfo);

    postEvent(DebugEvent.threadPausedEvent(buildThreadProto(threadId).build()));

    synchronized(pauseInfo) {
      try {
        pauseInfo.wait();
      } catch (InterruptedException e) {
      }
    }

    postEvent(DebugEvent.threadContinuedEvent(buildThreadProto(threadId).build()));
  }

  /**
   * Posts a debug event if a client is currently connected.
   *
   * @param event the event to post
   */
  private void postEvent(DebugEvent event) {
    try {
      postEventLock.lock();

      if (eventStream != null) {
        try {
          event.asEventProto().writeDelimitedTo(eventStream);
          eventStream.flush();
        } catch (IOException e) {
          System.err.println("Failed to post event: " + e.getMessage());
        }
      }
    } finally {
      postEventLock.unlock();;
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
      case SETBREAKPOINTS:
        response = handleSetBreakpointsRequest(sequenceNumber, request.getSetBreakpoints());
        break;
      case CONTINUEEXECUTION:
        response = handleContinueExecutionRequest(sequenceNumber, request.getContinueExecution());
        break;
      case EVALUATE:
        response = handleEvaluateRequest(sequenceNumber, request.getEvaluate());
        break;
      case LISTFRAMES:
        response = handleListFramesRequest(sequenceNumber, request.getListFrames());
        break;
      default:
        // TODO(allevato): Return an error response.
        keepRunning = false;
        break;
    }

    if (response != null) {
      postEvent(response);
    }

    return keepRunning;
  }

  /** Handles a {@code ListThreadsRequest} and returns its response. */
  private DebugEvent handleListThreadsRequest(
      long sequenceNumber, DebugProtos.ListThreadsRequest listThreads) throws IOException {
    ImmutableList.Builder<DebugProtos.Thread> threadListBuilder = ImmutableList.builder();

    // TODO(allevato): Create a separate thread lock and synchronize all thread-based operations
    // around it instead of having separate concurrent hash maps.
    synchronized (threadAdapters) {
      for (long threadId : threadAdapters.keySet()) {
        threadListBuilder.add(buildThreadProto(threadId).build());
      }
    }

    return DebugEvent.listThreadsResponse(sequenceNumber, threadListBuilder.build());
  }

  /** Handles a {@code SetBreakpointsRequest} and returns its response. */
  private DebugEvent handleSetBreakpointsRequest(
      long sequenceNumber, DebugProtos.SetBreakpointsRequest setBreakpoints) throws IOException {
    locationBreakpoints.clear();

    List<DebugProtos.Breakpoint> allBreakpoints = setBreakpoints.getBreakpointList();
    for (DebugProtos.Breakpoint breakpoint : allBreakpoints) {
      switch (breakpoint.getConditionCase()) {
        case LOCATION:
          locationBreakpoints.add(breakpoint.getLocation());
          break;
        default:
          // TODO(allevato): Handle an unknown breakpoint type? This would happen if the client was
          // newer than the server.
          break;
      }
    }

    System.err.println(locationBreakpoints);
    return DebugEvent.setBreakpointsResponse(sequenceNumber);
  }

  /** Handles a {@code ContinueExecutionRequest} and returns its response. */
  private DebugEvent handleContinueExecutionRequest(long sequenceNumber,
      DebugProtos.ContinueExecutionRequest continueExecution) throws IOException {
    long threadId = continueExecution.getThreadId();
    PausedThreadInfo pauseInfo = pausedThreads.remove(threadId);
    if (pauseInfo != null) {
      DebugAdapter adapter = threadAdapters.get(threadId);
      StepControl stepControl = adapter.stepControl(continueExecution.getStepping());
      if (stepControl == null) {
        stepControls.remove(threadId);
      } else {
        stepControls.put(threadId, stepControl);
      }
      synchronized (pauseInfo) {
        pauseInfo.notify();
      }
    }
    return DebugEvent.continueExecutionResponse(sequenceNumber);
  }

  /** Handles a {@code EvaluateRequest} and returns its response. */
  private DebugEvent handleEvaluateRequest(long sequenceNumber,
      DebugProtos.EvaluateRequest evaluate) throws IOException {
    long threadId = evaluate.getThreadId();
    String expression = evaluate.getExpression();

    DebugAdapter adapter = threadAdapters.get(threadId);
    if (adapter == null) {
      // TODO(allevato): Return error response.
      return DebugEvent.error(sequenceNumber,
          String.format("Thread %d is not running", threadId));
    }

    try {
      Object result = adapter.evaluate(expression);
      return DebugEvent.evaluateResponse(
          sequenceNumber, new DebugValueMirror(result).asValueProto(null));
    } catch (EvalException | InterruptedException e) {
      // TODO(allevato): Return error response.
      return DebugEvent.error(sequenceNumber, e.getMessage());
    }
  }

  /** Handles a {@code ContinueExecutionRequest} and returns its response. */
  private DebugEvent handleListFramesRequest(long sequenceNumber,
      DebugProtos.ListFramesRequest listFrames) throws IOException {
    long threadId = listFrames.getThreadId();

    synchronized (pausedThreads) {
      PausedThreadInfo pausedThread = pausedThreads.get(threadId);
      DebugAdapter adapter = threadAdapters.get(threadId);
      if (adapter == null) {
        // TODO(allevato): Return error response.
        return DebugEvent.error(sequenceNumber,
            String.format("Thread %d is not running", threadId));
      }

      Iterable<DebugProtos.Frame> frames = adapter.listFrames(pausedThread.astNode);
      return DebugEvent.listFramesResponse(sequenceNumber, frames);
    }
  }
}
