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

package org.prebake.service;

import org.prebake.fs.FileAndHash;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Allows {@link org.prebake.js.Loader loading} JavaScript files that are on the
 * classpath by tool and plan files.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class BuiltinResourceLoader {

  /**
   * Tries to satisfy a path from the classpath if possible.
   * If p is an absolute path under the
   * {@link #getBuiltinResourceRoot built-in resource root}, then this method
   * will try to serve it, but will otherwise return null to indicate that the
   * caller should fall back to another strategy.
   *
   * @param p an absolute path.
   * @return null if p should not be loaded from the classpath.
   * @throws IOException if p should be loaded from the classpath but cannot be.
   */
  public static FileAndHash tryLoad(Path p) throws IOException {
    p = p.normalize();
    Path root = getBuiltinResourceRoot(p);
    if (!p.startsWith(root)) { return null; }  // Can be serviced by others.
    // /--baked-in--/foo/bar.js -> foo/bar.js
    Path relPath = root.relativize(p);
    String relUriStr = relPath.toString();
    String sep = p.getFileSystem().getSeparator();
    if (!"/".equals(sep)) {
      relUriStr = relUriStr.replace(sep, "/");
    }
    if (relUriStr.indexOf('?') >= 0 || relUriStr.indexOf('#') >= 0
        || relUriStr.indexOf(':') >= 0) {
      throw new FileNotFoundException(p.toString());
    }
    InputStream in = BuiltinResourceLoader.class.getResourceAsStream(relUriStr);
    if (in == null) { throw new FileNotFoundException(p.toString()); }
    return FileAndHash.fromStream(p, in, false);
  }

  /**
   * On non-Windows OSes, JavaScript files under the {@code org.prebake.service}
   * package can be reached via the fake file-system {@code /--baked-in--/},
   * and on windows they can be reached via {@code C:\--baked-in--\} where C is
   * the partition holding the client root.
   */
  public static Path getBuiltinResourceRoot(Path clientRoot) {
    return clientRoot.getRoot().resolve("--baked-in--");
  }
}
