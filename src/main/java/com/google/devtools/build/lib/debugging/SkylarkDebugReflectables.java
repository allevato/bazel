package com.google.devtools.build.lib.debugging;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.debugging.SkylarkDebugView.Child;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/**
 * Created by allevato on 7/28/17.
 */
public final class SkylarkDebugReflectables {

  private static final SkylarkDebugView DEFAULT_DEBUG_VIEW = new SkylarkDebugView() {
    @Override
    public Iterable<Child> getChildren(Object parent) {
      return ImmutableList.<Child>builder().build();
    }
  };

  private static final SkylarkDebugView ITERABLE_DEBUG_VIEW = new SkylarkDebugView() {
    @Override
    public Iterable<Child> getChildren(Object parent) {
      Iterable<?> listValue = (Iterable<?>) parent;
      ImmutableList.Builder<Child> childrenBuilder = ImmutableList.builder();
      int index = 0;
      for (Object value : listValue) {
        childrenBuilder.add(new Child(String.format("[%d]", index++), value));
      }
      return childrenBuilder.build();
    }
  };

  private static final SkylarkDebugView ARRAY_DEBUG_VIEW = new SkylarkDebugView() {
    @Override
    public Iterable<Child> getChildren(Object parent) {
      ImmutableList.Builder<Child> childrenBuilder = ImmutableList.builder();
      for (int index = 0; index < Array.getLength(parent); index++) {
        Object value = Array.get(parent, index);
        childrenBuilder.add(new Child(String.format("[%d]", index++), value));
      }
      return childrenBuilder.build();
    }
  };

  private static final SkylarkDebugView MAP_DEBUG_VIEW = new SkylarkDebugView() {
    final class MapEntry implements SkylarkDebugReflectable {
      private final Object key;
      private final Object value;

      MapEntry(Object key, Object value) {
        this.key = key;
        this.value = value;
      }

      @Override
      public String toString() {
        return value.toString();
      }

      @Override
      public SkylarkDebugView getCustomDebugView() {
        return new SkylarkDebugView() {
          @Override
          public Iterable<Child> getChildren(Object parent) {
            MapEntry entry = (MapEntry) parent;
            return ImmutableList.of(
                new Child("key", entry.key),
                new Child("value", entry.value)
            );
          }
        };
      }
    }

    @Override
    public Iterable<Child> getChildren(Object parent) {
      Map<?, ?> mapValue = (Map<?, ?>) parent;
      ImmutableList.Builder<Child> childrenBuilder = ImmutableList.builder();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        Object key = entry.getKey();
        Object value = entry.getValue();

        // TODO(allevato): Should key.toString() here actually be a repr? If so, we need to pass
        // in an interface to abstract that out, because we don't want this package to have Skylark
        // dependencies.
        childrenBuilder.add(
            new Child(String.format("[%s]", key.toString()), new MapEntry(key, value)));
      }
      return childrenBuilder.build();
    }
  };

  public static Iterable<Child> getDebugViewChildren(Object parent) {
    SkylarkDebugView debugView;
    if (parent instanceof SkylarkDebugReflectable) {
      debugView = ((SkylarkDebugReflectable) parent).getCustomDebugView();
    } else if (parent instanceof Map) {
      debugView = MAP_DEBUG_VIEW;
    } else if (parent instanceof Iterable) {
      debugView = ITERABLE_DEBUG_VIEW;
    } else if (parent.getClass().isArray()) {
      debugView = ARRAY_DEBUG_VIEW;
    } else {
      debugView = DEFAULT_DEBUG_VIEW;
    }
    return debugView.getChildren(parent);
  }
}
