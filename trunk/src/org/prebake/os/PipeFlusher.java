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

package org.prebake.os;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.io.Closeables;

/**
 * Keeps {@link OsProcess external processes'} pipes from getting clogged
 * by shuttling bytes from one process's output stream to another process's
 * input stream.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class PipeFlusher implements Closeable {
  private boolean closed = false;
  /** Pipes that might need to be serviced. */
  private final ConcurrentLinkedQueue<Pipe> livePipes
      = new ConcurrentLinkedQueue<Pipe>();

  private final ScheduledExecutorService execer;

  public PipeFlusher(ScheduledExecutorService execer) {
    this.execer = execer;
  }

  void createPipe(OsProcess from, OsProcess to) throws InterruptedException {
    pushPipe(new Pipe(from, to));
  }

  private void pushPipe(Pipe p) throws InterruptedException {
    synchronized (this) {
      if (closed) { throw new InterruptedException(); }
    }
    livePipes.add(p);
  }

  private ScheduledFuture<?> pipeDispatcherFuture;

  public void start() {
    Runnable pipeDispatcher = new Runnable() {
      public void run() {
        boolean closed;
        synchronized (PipeFlusher.this) {
          closed = PipeFlusher.this.closed;
        }
        if (closed) {
          for (Pipe p; (p = livePipes.poll()) != null;) {
            OutputStream out = p.from.getOutputStream();
            if (out != null) { Closeables.closeQuietly(out); }
            InputStream in = p.to.getInputStream();
            if (in != null) { Closeables.closeQuietly(in); }
          }
          pipeDispatcherFuture.cancel(false);
          return;
        }
        for (Pipe p; (p = livePipes.poll()) != null;) {
          OutputStream out = p.to.getOutputStream();
          if (out == null) {
            p.to.noMoreInput();
            // Drop the pipe on the floor.
            continue;
          }
          InputStream in = p.from.getInputStream();
          try {
            int n = in.available();
            if (n > 0) {
              handlePipe(in, out, p);
            } else {
              try {
                pushPipe(p);
              } catch (InterruptedException ex) {
                // Drop the pipe on the floor.
              }
            }
          } catch (IOException ex) {
            // Drop the pipe on the floor.
          }
        }
      }
    };
    pipeDispatcherFuture = execer.scheduleWithFixedDelay(
        pipeDispatcher, 50, 50, TimeUnit.MILLISECONDS);
  }

  private void handlePipe(
      final InputStream in, final OutputStream out, final Pipe p) {
    execer.submit(new Runnable() {
      public void run() {
        try {
          byte[] buf = getBuffer(in.available());
          try {
            while (in.available() > 0) {
              out.write(buf, 0, in.read(buf));
            }
          } finally {
            releaseBuffer(buf);
          }
          try {
            pushPipe(p);
          } catch (InterruptedException ex) {
            // Drop the pipe on the floor.
          }
        } catch (IOException ex) {
       // Drop the pipe on the floor.
        }
      }
    });
  }

  private static final int BUF_SIZE = 4096;
  /** Pipes that need to be serviced. */
  private final Queue<byte[]> freeBuffers = new ConcurrentLinkedQueue<byte[]>();

  private byte[] getBuffer(int desiredSize) {
    if (desiredSize >= 1024) {
      byte[] buf = freeBuffers.poll();
      if (buf != null) { return buf; }
    }
    return new byte[Math.min(desiredSize, BUF_SIZE)];
  }

  private void releaseBuffer(byte[] buffer) {
    if (buffer.length == BUF_SIZE) {
      freeBuffers.offer(buffer);
    }
  }

  public void close() {
    synchronized (this) {
      closed = true;
      freeBuffers.clear();
    }
  }

  private static final class Pipe {
    final OsProcess from, to;

    Pipe(OsProcess from, OsProcess to) {
      this.from = from;
      this.to = to;
    }
  }
}
