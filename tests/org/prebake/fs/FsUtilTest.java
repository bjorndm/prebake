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

import org.prebake.util.PbTestCase;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.Test;

public class FsUtilTest extends PbTestCase {
  @Test public final void testNormalizeUNIX() {
    Path root = unixRoot();
    assertEquals("", FsUtil.normalizePath(root, ""));
    assertEquals("/", FsUtil.normalizePath(root, "/"));
    assertEquals("/foo/bar", FsUtil.normalizePath(root, "/foo/bar"));
    assertEquals("foo/bar", FsUtil.normalizePath(root, "foo/bar"));
    assertEquals("foo", FsUtil.normalizePath(root, "foo/"));
    assertEquals("FOO", FsUtil.normalizePath(root, "FOO"));
    assertEquals("foo\\bar.txt", FsUtil.normalizePath(root, "foo\\bar.txt"));
  }

  @Test public final void testNormalizeDOS() {
    Path root = dosRoot();
    assertEquals("", FsUtil.normalizePath(root, ""));
    assertEquals("/", FsUtil.normalizePath(root, "C:"));
    assertEquals("/", FsUtil.normalizePath(root, "C:\\"));
    assertEquals("/foo/bar", FsUtil.normalizePath(root, "C:\\foo\\bar"));
    assertEquals("foo/bar", FsUtil.normalizePath(root, "foo\\bar"));
    assertEquals("foo", FsUtil.normalizePath(root, "foo\\"));
    assertEquals("foo", FsUtil.normalizePath(root, "FOO"));
  }

  @Test public final void testDenormalizeUNIX() {
    Path root = unixRoot();
    assertEquals("", FsUtil.denormalizePath(root, ""));
    assertEquals("/", FsUtil.denormalizePath(root, "/"));
    assertEquals("/foo", FsUtil.denormalizePath(root, "/foo"));
    assertEquals("foo/", FsUtil.denormalizePath(root, "foo/"));
    assertEquals("foo", FsUtil.denormalizePath(root, "foo"));
    assertEquals("foo.txt", FsUtil.denormalizePath(root, "foo.txt"));
    assertEquals("foo/BAR.txt", FsUtil.denormalizePath(root, "foo/BAR.txt"));
  }

  @Test public final void testDenormalizeDOS() {
    Path root = dosRoot();
    assertEquals("", FsUtil.denormalizePath(root, ""));
    assertEquals("C:\\", FsUtil.denormalizePath(root, "/"));
    assertEquals("C:\\foo", FsUtil.denormalizePath(root, "/foo"));
    assertEquals("foo\\", FsUtil.denormalizePath(root, "foo/"));
    assertEquals("foo", FsUtil.denormalizePath(root, "foo"));
    assertEquals("foo.txt", FsUtil.denormalizePath(root, "foo.txt"));
    assertEquals("foo\\BAR.txt", FsUtil.denormalizePath(root, "foo/BAR.txt"));
  }

  private Path dosRoot() {
    try {
      return fileSystemFromAsciiArt("C:\\cwd", "C:\\").getRootDirectories()
          .iterator().next();
    } catch (IOException ex) {
      throw new IOError(ex);
    }
  }

  private Path unixRoot() {
    try {
      return fileSystemFromAsciiArt("/", "/").getRootDirectories()
          .iterator().next();
    } catch (IOException ex) {
      throw new IOError(ex);
    }
  }
}
