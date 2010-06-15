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

import com.google.common.collect.ImmutableMap;

/**
 * Abstracts away process management so we can build piping on top of
 * ProcessBuilder or implement it natively if need be.
 *
 * <p>
 * We assume that all setup every method that can only be called before
 * {@link OsProcess#run} happens in the same thread that {@code run} is called
 * in and that thereafter, this object needs to be thread-safe.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class OsProcess {
  private final OperatingSystem os;
  /** Resolved via the OS search path. */
  private final String command;
  /** Null if not yet running or if already finished. */
  private Process p;
  /** Exit value of p if it has finished running or MIN_VALUE otherwise. */
  private int result = Integer.MIN_VALUE;
  /** Optional process to which output is piped. */
  private OsProcess outReceiver;
  /** Files to hook to stdin/stdout. */
  private Path inFile, outFile;
  /** True iff there's a file or process on stdin. */
  private boolean receivingInput;
  /** True iff outFile should be truncated. */
  private boolean truncateOutput;
  /** True iff p should inherit the JVM's environment. */
  private boolean inheritEnvironment = true;
  /** Any process specific environment. */
  private ImmutableMap.Builder<String, String> environment
      = ImmutableMap.builder();

  // TODO: figure out how to get the error and output to the logger.

  protected OsProcess(
      OperatingSystem os, Path cwd, String command, String... argv) {
    this.os = os;
    this.command = command;
    setWorkdirAndCommand(cwd, command, argv);
  }

  protected abstract void setWorkdirAndCommand(
      Path cwd, String command, String... argv);

  public synchronized final OsProcess mergeOutput() {
    combineStdoutAndStderr();
    return this;
  }

  protected abstract void combineStdoutAndStderr();

  public synchronized final boolean kill() {
    if (p != null) {
      p.destroy();
      p = null;
    } else {
      preemptivelyKill();
    }
    // Normal results are one byte wide, so MIN_VALUE indicates has not run
    // or result has never been checked.
    return result == Integer.MIN_VALUE;
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
      @Nullable Path outFile, boolean truncateOutput, @Nullable Path inFile,
      ImmutableMap<String, String> environment, boolean inheritEnvironment)
      throws IOException;

  public synchronized final boolean runIfNotRunning()
      throws InterruptedException, IOException {
    if (!hasStartedRunning()) {
      run();
      return true;
    } else {
      return false;
    }
  }

  public synchronized final OsProcess run()
      throws InterruptedException, IOException {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    Path outFile = null, inFile = null;
    boolean inheritOutput = false, closeInput = false;
    if (this.outReceiver != null) {
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
    ImmutableMap<String, String> environment = this.environment.build();
    this.environment = null;
    p = startRunning(
        inheritOutput, closeInput, outFile, truncateOutput, inFile,
        environment, inheritEnvironment);
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
    truncateOutput = true;
    return this;
  }

  public synchronized final OsProcess appendTo(Path p) {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    if (outReceiver != null) { throw new IllegalStateException(); }
    outFile = p;
    truncateOutput = false;
    return this;
  }

  public synchronized final OsProcess env(String key, String value) {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    environment.put(key, value);
    return this;
  }

  public synchronized final OsProcess noInheritEnv() {
    if (hasStartedRunning()) { throw new IllegalStateException(); }
    inheritEnvironment = false;
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

  /**
   * Since the {@link #pipeTo} method not require that the recipient process is
   * started, we may be in the position of closing the processes input before it
   * has any.
   */
  final synchronized void noMoreInput() {
    receivingInput = false;
    if (p != null) {
      try {
        // The end of a pipe's input channel is exposed as an OutputStream in
        // the Java space.
        p.getOutputStream().close();
      } catch (IOException ex) {
        // OK
      }
    }
  }

  public final String getCommand() { return command; }

  @Override
  public String toString() { return "[OsProcess " + command + "]"; }
}
