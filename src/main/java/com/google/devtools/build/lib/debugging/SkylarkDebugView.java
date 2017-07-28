package com.google.devtools.build.lib.debugging;

/**
 * Created by allevato on 7/28/17.
 */
public interface SkylarkDebugView {

  Iterable<Child> getChildren(Object parent);

  class Child {
    private String label;
    private Object value;

    public Child(String label, Object value) {
      this.label = label;
      this.value = value;
    }

    public String getLabel() {
      return label;
    }

    public Object getValue() {
      return value;
    }
  }
}
