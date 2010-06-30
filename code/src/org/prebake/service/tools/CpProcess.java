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

package org.prebake.service.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.io.ByteStreams;

/**
 * Implements file copying in the VM.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class CpProcess implements InVmProcess {
  public byte run(Path workingDir, String... argv) throws IOException {
    for (int i = 0, n = argv.length; i < n; i += 2) {
      Path src = workingDir.resolve(argv[i]);
      Path tgt = workingDir.resolve(argv[i + 1]);
      InputStream in = src.newInputStream();
      try {
        // Fail if the target already exists.
        OutputStream out = tgt.newOutputStream(StandardOpenOption.CREATE_NEW);
        try {
          ByteStreams.copy(in, out);
        } finally {
          out.close();
        }
      } finally {
        in.close();
      }
    }
    return 0;
  }
}
