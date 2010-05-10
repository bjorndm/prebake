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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * not reaching into the client directory is accepted as best practice.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class WorkingFileChecker {
  private final Path clientDir;
  private final Path workingDir;
  private final Pattern fingerprint;

  WorkingFileChecker(Path clientDir, Path workingDir) {
    assert clientDir.isAbsolute();
    assert workingDir.isAbsolute();
    this.clientDir = clientDir;
    this.workingDir = workingDir;
    this.fingerprint = makeFingerprint(
        workingDir.relativize(clientDir).toString(),
        // DOS paths are case-insensitive
        !"/".equals(workingDir.getFileSystem().getSeparator()));
  }

  /**
   * Applied to paths to make sure they
   * don't reach into the client directory.
   */
  Path check(Path p) throws IOException {
    if (workingDir.resolve(p).normalize().startsWith(clientDir)) {
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
    Matcher m = fingerprint.matcher(s);
    if (m.find()) {
      throw new IllegalArgumentException(
          "Please do not touch files in the client directory during builds: "
          + m.group() + " in " + s);
    }
    return s;
  }

  private static Pattern makeFingerprint(String path, boolean caseInsensitive) {
    StringBuilder sb = new StringBuilder(path.length());
    boolean lastInFingerprint = false, sawFingerprint = false;
    int pos = 0;
    sb.append("(?:^|[\\W])");
    int n = path.length();
    for (int i = 0; i <= n; ++i) {
      boolean inFingerprint = i < n ? inFingerprint(path.charAt(i)) : false;
      if (lastInFingerprint != inFingerprint) {
        if (!inFingerprint) {
          if (sawFingerprint) { sb.append("[/.\\\\]+"); }
          sb.append(Pattern.quote(path.substring(pos, i)));
          sawFingerprint = true;
        } else {
          pos = i;
        }
        lastInFingerprint = inFingerprint;
      }
    }
    if (sawFingerprint) {
      sb.append("(?:$|[;:,#\"'/*?()\\\\])");
    }
    return Pattern.compile(
        sb.toString(), caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
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
