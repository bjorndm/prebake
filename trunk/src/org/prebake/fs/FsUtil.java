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

import java.nio.file.Path;
import com.google.caja.util.Strings;

/**
 * Utilities for converting from normalized paths to real paths and back.
 *
 * <p>
 * A normalized path is one where <tt>/</tt> is the separator, and the file root
 * is <tt>/</tt>.
 * On UNIX systems, a normalized path is the same as the real path, but on DOS
 * based file-systems, the separator is <tt>\</tt> and the file root is a
 * partition like <tt>C:</tt>.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class FsUtil {
  /** Converts from a real path to a normalized path. */
  public static String normalizePath(Path root, String path) {
    String sep = root.getFileSystem().getSeparator();
    int n = path.length();
    if (!"/".equals(sep)) {
      path = path.replace(sep, "/");
      String rootName = root.toString();
      if (path.startsWith(rootName)) {
        path = path.substring(rootName.length());
        if (!path.startsWith("/")) { path = "/" + path; }
      }
      path = Strings.toLowerCase(path);  // Case fold for DOS.
      n = path.length();
    }
    return n <= 1 || !path.endsWith("/") ? path : path.substring(0, n - 1);
  }

  /** Converts from a normalized path to a real path. */
  public static String denormalizePath(Path root, String path) {
    String sep = root.getFileSystem().getSeparator();
    if ("/".equals(sep)) { return path; }
    // Convert DOS paths to native
    // TODO: if the normalized path is not all lower-case, should we fail?
    boolean isAbs = path.startsWith("/");
    path = path.replace("/", sep);
    if (isAbs) { path = root + path; }
    return path;
  }
}
