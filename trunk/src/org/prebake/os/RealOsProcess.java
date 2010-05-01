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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * A process backed by a real {@code ProcessBuilder}
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class RealOsProcess extends OsProcess {
  private ProcessBuilder pb;

  RealOsProcess(OperatingSystem os, Path cwd, String cmd, String... argv) {
    super(os, cwd, cmd, argv);
  }

  @Override protected void setWorkdirAndCommand(
      Path cwd, String cmd, String... argv) {
    pb = new ProcessBuilder();
    int argc = argv.length;
    String[] combined = new String[argc + 1];
    combined[0] = cmd;
    System.arraycopy(argv, 0, combined, 1, argc);
    pb.command(combined);
    pb.directory(new File(cwd.toUri()));
  }

  @Override protected void combineStdoutAndStderr() {
    pb.redirectErrorStream(true);
  }

  @Override protected void preemptivelyKill() { pb = null; }

  @Override protected boolean hasStartedRunning() { return pb == null; }

  @Override
  protected Process startRunning(
      boolean inheritOutput, boolean closeInput,
      @Nullable Path outFile, @Nullable Path inFile)
      throws IOException {
    ProcessBuilder pb = this.pb;
    this.pb = null;
    if (inheritOutput) {
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    }
    if (outFile != null) {
      assert outFile.getFileSystem() == FileSystems.getDefault();
      pb.redirectOutput(new File(outFile.toUri()));
    }
    if (!pb.redirectErrorStream()) {
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    }
    if (inFile != null) {
      assert inFile.getFileSystem() == FileSystems.getDefault();
      pb.redirectInput(new File(inFile.toUri()));
    }
    Process p = pb.start();
    if (closeInput) { p.getInputStream().close(); }
    return p;
  }
}
