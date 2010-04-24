package org.prebake.util;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Utilities for dealing with object graphs.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class ObjUtil {
  private static final Set<Class<?>> IMMUTABLE_CLASSES
      = ImmutableSet.<Class<?>>builder()
      .add(String.class)
      .add(Integer.class)
      .add(Void.class)
      .add(Byte.class)
      .add(Short.class)
      .add(Long.class)
      .add(Float.class)
      .add(Boolean.class)
      .add(Character.class)
      .add(Double.class)
      .build();

  public static boolean isDeeplyImmutable(@Nullable Object o) {
    if (o == null) { return true; }
    if (IMMUTABLE_CLASSES.contains(o.getClass())) { return true; }
    if (o instanceof ImmutableCollection<?>) {
      return isDeeplyImmutable((ImmutableCollection<?>) o);
    }
    if (o instanceof ImmutableMap<?, ?>) {
      return isDeeplyImmutable((ImmutableMap<?, ?>) o);
    }
    if (o.getClass().isArray() && Array.getLength(o) == 0) { return true; }
    return false;
  }

  public static boolean isDeeplyImmutable(ImmutableMap<?, ?> m) {
    for (Map.Entry<?, ?> e : m.entrySet()) {
      if (!(isDeeplyImmutable(e.getKey()) && isDeeplyImmutable(e.getValue()))) {
        return false;
      }
    }
    return true;
  }

  public static boolean isDeeplyImmutable(ImmutableCollection<?> c) {
    for (Object o : c) { if (!isDeeplyImmutable(o)) { return false; } }
    return true;
  }

  private ObjUtil() { /* not instantiable */ }
}
