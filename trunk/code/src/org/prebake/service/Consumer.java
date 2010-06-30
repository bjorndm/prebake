// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.prebake.service;

import java.io.Closeable;
import java.lang.Thread.UncaughtExceptionHandler;
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
              UncaughtExceptionHandler h
                  = Thread.getDefaultUncaughtExceptionHandler();
              if (h != null) {
                h.uncaughtException(Thread.currentThread(), ex);
              } else {
                ex.printStackTrace();
              }
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
