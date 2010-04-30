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

package org.prebake.service.bake;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Gates access to files specified by running actions to prevent unintentional
 * access to files in the client directory.
 *
 * <p>
 * This is heuristic and so likely to result in annoying corner cases.
 * This should probably be treated as training wheels until users understand
 * how reaching into the client directory breaks the repeatability of builds.
 * Maybe this won't cause any appreciable number of failures which would be
 * great, but if it does, then we can take off the training wheels once
 * not reaching into the client dir is accepted as best practice.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class WorkingFileChecker {
  final Path clientDir;
  final Path workingDir;
  final String fingerPrint;

  WorkingFileChecker(Path clientDir, Path workingDir) {
    assert clientDir.isAbsolute();
    assert workingDir.isAbsolute();
    this.clientDir = clientDir;
    this.workingDir = workingDir;
    this.fingerPrint = makeFingerprint(
        workingDir.relativize(clientDir).toString());
  }

  /**
   * Applied to paths to make sure they
   * don't reach into the client directory.
   */
  Path check(Path p) throws IOException {
    if (workingDir.resolve(p).startsWith(clientDir)) {
      throw new IOException(
          "Please do not touch files in the client directory during builds: "
          + p);
    }
    return p;
  }

  /**
   * Applied to command line arguments to make sure they don't reach into the
   * client directory.
   * @return s the input if safe.
   */
  String check(String s) throws IllegalArgumentException {
    int m = s.length(), n = fingerPrint.length();
    assert n != 0;
    if (m < n) { return s; }
    for (int i = 0, j = 0; i < m; ++i) {
      char ch = s.charAt(i);
      if (inFingerprint(ch)) {
        if (j == n || ch != fingerPrint.charAt(j)) { return s; }
        ++j;
      }
    }
    throw new IllegalArgumentException(
        "Please do not touch files in the client directory during builds: "
        + s);
  }

  private static String makeFingerprint(String path) {
    StringBuilder sb = new StringBuilder(path.length());
    for (int i = 0, n = path.length(); i < n; ++i) {
      char ch = path.charAt(i);
      if (inFingerprint(ch)) { sb.append(ch); }
    }
    return sb.toString();
  }

  private static final boolean inFingerprint(char ch) {
    switch (ch) {
      case '.':  // Show up in . and ..
      case ':':  // Show up in windows root directories
      case '\\': case '/':  // File separators are exchangeable.
        return false;
      default: return true;
    }
  }
}
