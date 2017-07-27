package com.google.devtools.build.lib.syntax.debugserver;

import com.google.devtools.build.lib.syntax.Environment;

/**
 */
public interface StepControl {

  boolean shouldPause(Environment env);
}
