package org.prebake.service;

import java.io.Closeable;
import java.util.concurrent.BlockingQueue;

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
      if (q.peek() != null) { mutex.wait(); }
    }
  }

  public void close() {
    mutex.notifyAll();
    q = null;
    if (th != null) { th.interrupt(); }
  }

  protected abstract void consume(BlockingQueue<? extends T> q, T x);
}
