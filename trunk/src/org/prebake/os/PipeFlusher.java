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
import java.util.concurrent.ArrayBlockingQueue;
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
  // TODO: unittests
  private boolean closed = false;
  /** Pipes that might need to be serviced. */
  private final ConcurrentLinkedQueue<Pipe> livePipes
      = new ConcurrentLinkedQueue<Pipe>();

  private final ScheduledExecutorService execer;

  public PipeFlusher(ScheduledExecutorService execer) {
    assert execer != null;
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

  /**
   * There's no way to check whether an input stream is closed without blocking
   * on it, so periodically (after 10 empty reads), we do a blocking read.
   */
  private void checkClosed(final Pipe p) {
    execer.submit(new Runnable() {
      public void run() {
        try {
          InputStream in = p.from.getInputStream();
          int read = in.read();
          if (read < 0) {
            dropPipe(p);
          } else {
            doHandlePipe(in, read, p.to.getOutputStream(), p);
          }
        } catch (IOException ex) {
          dropPipe(p);
        }
      }
    });
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
            dropPipe(p);
            continue;
          }
          InputStream in = p.from.getInputStream();
          try {
            int n = in.available();
            if (n > 0) {
              handlePipe(in, out, p);
            } else {
              try {
                if (p.noneAvailableCount++ >= 10) {
                  checkClosed(p);
                } else {
                  pushPipe(p);
                }
              } catch (InterruptedException ex) {
                dropPipe(p);
              }
            }
          } catch (IOException ex) {
            dropPipe(p);
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
        doHandlePipe(in, -1, out, p);
      }
    });
  }

  private void doHandlePipe(
      final InputStream in, int firstByte, final OutputStream out,
      final Pipe p) {
    try {
      byte[] buf = getBuffer(in.available());
      try {
        if (firstByte >= 0) {
          buf[0] = (byte) firstByte;
          if (in.available() > 0) {
            int nRead = in.read(buf, 1, buf.length - 1);
            out.write(buf, 0, nRead >= 0 ? nRead + 1 : 1);
          }
        }
        while (in.available() > 0) { out.write(buf, 0, in.read(buf)); }
      } finally {
        releaseBuffer(buf);
      }
      p.noneAvailableCount = 0;
      try {
        pushPipe(p);
      } catch (InterruptedException ex) {
        dropPipe(p);
      }
    } catch (IOException ex) {
      dropPipe(p);
    }
  }

  private static final int BUF_SIZE = 4096;
  private final Queue<byte[]> freeBuffers = new ArrayBlockingQueue<byte[]>(4);

  private byte[] getBuffer(int desiredSize) {
    if (desiredSize >= 1024) {
      byte[] buf = freeBuffers.poll();
      if (buf != null) { return buf; }
    }
    return new byte[Math.min(desiredSize, BUF_SIZE)];
  }

  private void releaseBuffer(byte[] buffer) {
    if (buffer.length == BUF_SIZE) {
      if (!freeBuffers.offer(buffer)) {
        // OK.  If we don't add it, then there are plenty available.
      }
    }
  }

  /** Called when a pipe's input has been drained. */
  private void dropPipe(Pipe p) {
    // Don't requeue.  Make sure that the recipient process knows it won't
    // getting any more input.
    Closeables.closeQuietly(p.from.getOutputStream());
    p.to.noMoreInput();
  }

  public void close() {
    synchronized (this) {
      closed = true;
      freeBuffers.clear();
    }
  }

  private static final class Pipe {
    final OsProcess from, to;
    int noneAvailableCount = 0;

    Pipe(OsProcess from, OsProcess to) {
      this.from = from;
      this.to = to;
    }

    @Override public String toString() {
      return from.getCommand() + "|" + to.getCommand();
    }
  }
}
