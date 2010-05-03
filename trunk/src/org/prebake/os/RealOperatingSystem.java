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
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An {@link OperatingSystem} that executes real processes.  Not a testing
 * stub or a wrapper.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class RealOperatingSystem implements OperatingSystem, Closeable {
  private final FileSystem fs;
  private final PipeFlusher flusher;

  public RealOperatingSystem(FileSystem fs, ScheduledExecutorService execer) {
    this.fs = fs;
    this.flusher = new PipeFlusher(execer);
    this.flusher.start();
  }

  public Path getTempDir() {
    return fs.getPath(System.getProperty("java.io.tmpdir"));
  }
  public OsProcess run(Path cwd, String command, String... argv) {
    return new RealOsProcess(this, cwd, command, argv);
  }
  public PipeFlusher getPipeFlusher() { return flusher; }

  public void close() { flusher.close(); }
}
