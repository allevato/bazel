
package com.google.devtools.build.lib.syntax.debugserver;

import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.debugprotocol.DebugProtos;

/** A bridge that allows the debug server to request information from a Skylark environment. */
public interface DebugAdapter {

  /** Evaluates a Skylark expression in the adapter's environment. */
  Object evaluate(String expression) throws EvalException, InterruptedException;

  /** Returns an iterable of the current stack frames of the adapter's environment. */
  Iterable<DebugProtos.Frame> listFrames();
}
