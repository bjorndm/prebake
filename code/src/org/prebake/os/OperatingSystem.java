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

import java.nio.file.Path;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstraction over {@link ProcessBuilder} to ease testing.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface OperatingSystem {
  /**
   * @param cwd the working directory for the process.
   * @param command e.g. {@code "cp"}.
   * @param argv the arguments to the command,
   *   e.g. {@code fromfile.txt}, {@code tofile.txt}.
   */
  OsProcess run(Path cwd, String command, String... argv);
  /**
   * A path to a directory where temporary files and directories can be put.
   * It should be on a mount that has enough space to mirror large chunks of
   * the client directory.
   *
   * <p>Normally, this is the directory named in the System property
   * {@code java.io.tmpdir}.
   */
  Path getTempDir();
  /** Manages piping data between processes. */
  PipeFlusher getPipeFlusher();
}
