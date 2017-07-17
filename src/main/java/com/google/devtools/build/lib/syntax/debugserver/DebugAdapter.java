
package com.google.devtools.build.lib.syntax.debugserver;

import com.google.devtools.build.lib.syntax.EvalException;

/** A bridge that allows the debug server to request information from a Skylark environment. */
public interface DebugAdapter {

  /** Evaluates a Skylark expression in the adapter's environment. */
  Object evaluate(String expression) throws EvalException, InterruptedException;
}
