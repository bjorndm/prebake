package org.prebake.util;

import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StubScheduledExecutorService implements ScheduledExecutorService {
  private PriorityQueue<Task<?>> tasks = new PriorityQueue<Task<?>>();
  private boolean shutdown;
  private long t;

  public void advanceTime(long dtMillis, Logger logger) {
    long t1 = t + dtMillis;
    while (!tasks.isEmpty()) {
      Task<?> task = tasks.peek();
      if (task.time > t1) { break; }
      task = tasks.poll();
      try {
        task.get();
      } catch (Exception ex) {
        logger.log(Level.WARNING, "Task failed", ex);
      }
      if (!shutdown && task.dt > 0) {
        task.time += task.dt;
        tasks.add(task);
      }
    }
  }

  public ScheduledFuture<?> schedule(Runnable r, long t, TimeUnit u) {
    return schedule(toCallable(r), t, u);
  }

  public <V> ScheduledFuture<V> schedule(Callable<V> c, long t, TimeUnit u) {
    Task<V> task = new Task<V>(c, t + TimeUnit.MILLISECONDS.convert(t, u));
    tasks.add(task);
    return task;
  }

  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable r, long t, long dt, TimeUnit u) {
    return scheduleWithFixedDelay(r, t, dt, u);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable r, long t, long dt, TimeUnit u) {
    Task<?> task = new Task<Void>(
        toCallable(r), t + TimeUnit.MILLISECONDS.convert(t, u),
        TimeUnit.MILLISECONDS.convert(dt, u));
    tasks.add(task);
    return task;
  }

  public boolean awaitTermination(long t, TimeUnit u)
      throws InterruptedException {
    if (tasks == null) { return true; }
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
    return false;
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> c)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> c, long t, TimeUnit u)
      throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> callables)
      throws InterruptedException, ExecutionException {
    throw new UnsupportedOperationException();
  }

  public <T> T invokeAny(
      Collection<? extends Callable<T>> callables, long t, TimeUnit u)
      throws InterruptedException, ExecutionException, TimeoutException {
    throw new UnsupportedOperationException();
  }

  public boolean isShutdown() {
    return shutdown;
  }

  public boolean isTerminated() {
    return tasks == null;
  }

  public void shutdown() {
    shutdown = true;
    if (tasks != null && tasks.isEmpty()) { tasks = null; }
  }

  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  public <T> Future<T> submit(Callable<T> c) {
    Task<T> task = new Task<T>(c, t);
    tasks.add(task);
    return task;
  }

  public Future<?> submit(Runnable r) { return submit(toCallable(r)); }

  public <T> Future<T> submit(final Runnable r, final T result) {
    return submit(new Callable<T>() {
      public T call() throws Exception {
        r.run();
        return result;
      }
    });
  }

  public void execute(Runnable r) { submit(r); }

  private static Callable<Void> toCallable(final Runnable r) {
    return new Callable<Void>() {
      public Void call() {
        r.run();
        return null;
      }
    };
  }

  private final class Task<T> implements ScheduledFuture<T> {
    final Callable<T> toRun;
    final long dt;
    long time;
    T result;
    boolean cancelled, scheduled, done;

    Task(Callable<T> toRun, long time, long dt) {
      this.toRun = toRun;
      this.time = time;
      this.dt = dt;
    }

    Task(Callable<T> toRun, long time) { this(toRun, time, -1); }

    public int compareTo(Delayed o) {
      long otime = o instanceof Task<?>
          ? ((Task<?>) o).time : t + o.getDelay(TimeUnit.MILLISECONDS);
      if (this.time < otime) { return -1; }
      if (this.time != otime) { return 1; }
      // consistent with equals
      long delta = ((long) System.identityHashCode(this))
          - System.identityHashCode(o);
      return delta < 0 ? -1 : delta != 0 ? 1 : 0;
    }

    @Override public boolean equals(Object o) { return this == o; }

    @Override public int hashCode() { return System.identityHashCode(this); }

    public boolean cancel(boolean interrupt) {
      if (cancelled) { return false; }
      cancelled = true;
      if (!scheduled) { return false; }
      return true;
    }

    public T get() throws InterruptedException, ExecutionException {
      if (!done) {
        try {
          result = toRun.call();
        } catch (Exception ex) {
          throw new ExecutionException(ex);
        }
        done = true;
      }
      return result;
    }

    public T get(long t, TimeUnit u)
        throws InterruptedException, ExecutionException, TimeoutException {
      if (dt == -1) { return get(); }
      if (done) { return result; }
      throw new TimeoutException();
    }

    public boolean isCancelled() { return cancelled; }

    public boolean isDone() { return done; }

    public long getDelay(TimeUnit u) {
      return u.convert(dt, TimeUnit.MILLISECONDS);
    }
  }
}
