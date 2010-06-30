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

package org.prebake.util;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ack!  Our test case utilities are complicated enough to warrant tests.
 */
public class PbTestCaseTest {
  private static final boolean DISABLED = true;  // TODO

  @Test public final void testFileSystemFromAsciiArt() throws IOException {
    PbTestCase tc = new PbTestCase() { /* concrete */ };
    FileSystem fs = tc.fileSystemFromAsciiArt(
        "/foo/",
        Joiner.on('\n').join(
            "/",
            "  foo/",
            "    boo.txt (r) \"Howdy!\"",
            "  bar/",
            "    baz.txt \"Hello, World!\"",
            "    far.txt"));
    for (String path : new String[] {
           "/", "/foo", "/foo/boo.txt", "/bar", "/bar/baz.txt", "/bar/far.txt"
         }) {
      assertTrue("'" + path + "'", fs.getPath(path).exists());
    }
    assertTrue(
        fs.getPath("/foo")
            .getFileAttributeView(BasicFileAttributeView.class).readAttributes()
            .isDirectory());
    assertTrue(
        fs.getPath("/foo/boo.txt")
            .getFileAttributeView(BasicFileAttributeView.class).readAttributes()
            .isRegularFile());
    if (!DISABLED) {  // file permissions not implemented in stub FS
    assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE),
        fs.getPath("/foo")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE),
        fs.getPath("/bar/baz.txt")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    assertEquals(
        ImmutableSet.of(PosixFilePermission.OWNER_READ),
        fs.getPath("/foo/boo.txt")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    }
    InputStream in = fs.getPath("/bar/baz.txt").newInputStream();
    String out;
    try {
      out = new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
    } finally {
      in.close();
    }
    assertEquals("Hello, World!", out);
    fs.close();
  }
}
