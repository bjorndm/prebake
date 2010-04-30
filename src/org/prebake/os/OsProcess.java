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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Abstracts away process management so we can build piping on top of
 * ProcessBuilder or implement it natively if need be.
 *
 * <p>
 * We assume that all setup every method that can only be called before
 * {@link OsProcess#run} happens in the same thread that {@code run} is called
 * in and that thereafter, this object needs to be thread-safe.
 *
 * @author mikesamuel@gmail.com
 */
public abstract class OsProcess {
  private final OperatingSystem os;
  private Process p = null;
  private OsProcess outReceiver;
  private Path inFile, outFile;
  private boolean receivingInput;
  private int result;

  // TODO: figure out how to get the error and output to the logger.

  protected OsProcess(
      OperatingSystem os, Path cwd, String command, String... argv) {
    this.os = os;
    setWorkdirAndCommand(cwd, command, argv);
  }

  protected abstract void setWorkdirAndCommand(
      Path cwd, String command, String... argv);

  public synchronized final OsProcess mergeOutput() {
    combineStdoutAndStderr();
    return this;
  }

  protected abstract void combineStdoutAndStderr();

  public synchronized final void kill() {
    if (p != null) {
      p.destroy();
      p = null;
    } else {
      preemptivelyKill();
    }
  }

  protected abstract void preemptivelyKill();

  public synchronized final OsProcess pipeTo(OsProcess outReceiver) {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    if (outFile != null) { throw new IllegalStateException(); }
    synchronized (outReceiver) {
      if (outReceiver.receivingInput) { throw new IllegalStateException(); }
      this.outReceiver = outReceiver;
      outReceiver.receivingInput = true;
    }
    return this;
  }

  protected abstract boolean hasStartedRunning();

  public synchronized final OsProcess readFrom(Path p) {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    if (receivingInput) { throw new IllegalStateException(); }
    inFile = p;
    receivingInput = true;
    return this;
  }

  protected abstract Process startRunning(
      boolean inheritOutput, boolean closeInput,
      @Nullable Path outFile, @Nullable Path inFile)
      throws IOException;

  public synchronized final void runIfNotRunning()
      throws InterruptedException, IOException {
    if (!hasStartedRunning()) { run(); }
  }

  public synchronized final OsProcess run()
      throws InterruptedException, IOException {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    Path outFile = null, inFile = null;
    boolean inheritOutput = false, closeInput = false;
    if (this.outReceiver == null) {
      // handle below
    } else if (this.outFile != null) {
      outFile = this.outFile;
      this.outFile = null;
    } else {
      inheritOutput = true;
    }
    if (this.inFile != null) {
      inFile = this.inFile;
      this.inFile = null;
    }
    if (this.outReceiver != null) {
      OsProcess outReceiver = this.outReceiver;
      this.outReceiver = null;
      // Pipe creation can throw an interrupted exception.  We handle it here
      // to keep possibly failing operations after any cleanup that happens
      // above.
      os.getPipeFlusher().createPipe(this, outReceiver);
    }
    p = startRunning(inheritOutput, closeInput, outFile, inFile);
    return this;
  }

  public final int waitFor() throws InterruptedException {
    Process p;
    synchronized (this) {
      if (!hasStartedRunning()) { throw new IllegalStateException(); }
      p = this.p;
      if (p == null) { return result; }
    }
    int result = p.waitFor();
    synchronized (this) {
      this.result = result;
      this.p = null;
    }
    return result;
  }

  public synchronized final OsProcess writeTo(Path p) {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    if (outReceiver != null) { throw new IllegalStateException(); }
    outFile = p;
    return this;
  }

  synchronized final boolean mightBeAlive() {
    return p != null;
  }

  final synchronized InputStream getInputStream() {
    return p != null ? p.getInputStream() : null;
  }

  final synchronized OutputStream getOutputStream() {
    return p != null ? p.getOutputStream() : null;
  }

  final synchronized void noMoreInput() {
    receivingInput = false;
    if (p != null) {
      try {
        p.getInputStream().close();
      } catch (IOException ex) {
        // OK
      }
    }
  }
}
