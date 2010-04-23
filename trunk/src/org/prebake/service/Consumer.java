package org.prebake.service;

import java.io.Closeable;
import java.util.concurrent.BlockingQueue;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
abstract class Consumer<T> implements Closeable {
  private BlockingQueue<? extends T> q;
  private Thread th;
  private final Object mutex = new Object();

  Consumer(BlockingQueue<? extends T> q) {
    this.q = q;
  }

  void start() {
    assert th == null && q != null;
    th = new Thread(new Runnable() {
      public void run() {
        try {
          while (true) {
            T one;
            try {
              synchronized (mutex) {
                if (q == null) { break; }
                one = q.poll();
                if (one == null) { mutex.notifyAll(); }
              }
              if (one == null) { one = q.take(); }
            } catch (InterruptedException ex) {
              break;
            }
            try {
              consume(q, one);
            } catch (RuntimeException ex) {
              Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
                  Thread.currentThread(), ex);
            }
          }
        } finally {
          q = null;
        }
      }
    });
    th.setDaemon(true);
    th.start();
  }

  void waitUntilEmpty() throws InterruptedException {
    synchronized (mutex) {
      while (q.peek() != null) { mutex.wait(); }
    }
  }

  public void close() {
    synchronized (mutex) {
      q = null;
      if (th != null) { th.interrupt(); }
      mutex.notifyAll();
    }
  }

  protected abstract void consume(BlockingQueue<? extends T> q, T x);
}
