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

package org.prebake.fs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A test primitive that models a pipe between processes when both ends of the
 * pipe are being tested in the same memory space.
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class StubPipe implements Closeable {
  private final Object mutex = new Object();
  public final InputStream in;
  public final OutputStream out;
  private final byte[] buf;
  private int pos;
  private int len;
  private boolean inClosed, outClosed, closing;

  /** @param bufSize the buffering capacity of the pipe. */
  public StubPipe(int bufSize) {
    assert bufSize != 0;
    this.buf = new byte[bufSize];
    this.in = new InputStream() {
      @Override public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) == 1 ? one[0] : -1;
      }

      @Override public int read(byte[] outBuf, int start, int outLen)
          throws IOException {
        synchronized (mutex) {
          int nRead = 0;
          while (!inClosed) {
            nRead = min3(outLen, len, buf.length - pos);
            if (nRead != 0) { break; }
            if (outClosed && len == 0) { return -1; }
            try {
              mutex.wait();
            } catch (InterruptedException ex) {
              throw new IOException();
            }
          }
          System.arraycopy(buf, pos, outBuf, start, nRead);
          pos = (pos + nRead) % buf.length;
          len -= nRead;
          return nRead;
        }
      }

      @Override public void close() throws IOException {
        synchronized (mutex) {
          if (!inClosed) {
            inClosed = true;
            mutex.notifyAll();
            if (outClosed && !closing) { StubPipe.this.close(); }
          }
        }
      }
    };
    this.out = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
      }

      @Override
      public void write(byte[] inBuf, int start, int inLen)
          throws IOException {
        synchronized (mutex) {
          int end = start + inLen;
          while (start < end && !outClosed) {
            int writePos = (pos + len) % buf.length;
            int nToWrite = min3(
                end - start, buf.length - len, buf.length - writePos);
            if (nToWrite != 0) {
              System.arraycopy(inBuf, start, buf, writePos, nToWrite);
              start += nToWrite;
              len += nToWrite;
            } else {
              try {
                mutex.wait();
              } catch (InterruptedException ex) {
                throw new IOException();
              }
            }
          }
          if (start < end) { throw new IOException(); }
        }
      }

      @Override public void flush() { /* noop */ }

      @Override public void close() throws IOException {
        synchronized (mutex) {
          if (!outClosed) {
            outClosed = true;
            mutex.notifyAll();
            if (inClosed && !closing) { StubPipe.this.close(); }
          }
        }
      }
    };
  }

  public void close() throws IOException {
    IOException failure = null;
    synchronized (mutex) {
      closing = true;
      if (!inClosed) {
        try {
          in.close();
        } catch (IOException ex) {
          failure = ex;
        }
      }
      if (!outClosed) {
        try {
          out.close();
        } catch (IOException ex) {
          if (failure == null) { failure = ex; }
        }
      }
      closing = false;
      mutex.notifyAll();
    }
    if (failure != null) { throw failure; }
  }

  public boolean isClosed() {
    synchronized (mutex) { return inClosed && outClosed; }
  }

  private static int min3(int a, int b, int c) {
    if (a > b) { a = b; }
    return a < c ? a : c;
  }
}
