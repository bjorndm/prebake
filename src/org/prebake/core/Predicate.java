package org.prebake.core;

public interface Predicate<T> {
  public boolean apply(T arg);
}
