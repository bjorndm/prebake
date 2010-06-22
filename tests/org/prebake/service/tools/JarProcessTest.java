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

import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

import org.junit.After;
import org.junit.Test;

public class JarProcessTest extends PbTestCase {
  // Rather than testing the byte correctness of ZIP files, we concentrate on
  // testing that the create method can create content readable by the extract
  // method and vice-versa.

  private FileSystem fs;

  @After public void tearDown() throws IOException {
    if (fs != null) {
      fs.close();
      fs = null;
    }
  }

  @Test public final void testSimpleZip() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/",
        "/",
        "  read/",
        "    foo.txt \"FOO\"",
        "    bar.txt \"BAR\"",
        "    baz/",
        "      boo.txt \"BOO\"",
        "  write/",
        "  one-file/");
    JarProcess h = new JarProcess();
    byte result = h.run(
        fs.getPath("/"), "c", "/write/foo.jar",
        "-1",  // No manifest
        // Go into the read dir and create entries for the following n files.
        "read", "3", "foo.txt", "bar.txt", "baz/boo.txt",
        // From the working dir above, create an entry for the following file.
        "", "1", "read/bar.txt");
    assertEquals((byte) 0, result);

    result = h.run(
        fs.getPath("/write"), "x", "foo.jar",
        // Extract all the files in foo.jar into /write
        "**",
        // Extract all files matching * into /write/top-level.
        "top-level///*");
    assertEquals((byte) 0, result);

    // Extract bar.txt from /write/foo.jar into /one-file
    result = h.run(
        fs.getPath("/one-file"), "x", "/write/foo.jar",
        "bar.txt");
    assertEquals((byte) 0, result);

    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  read/",
            "    foo.txt \"FOO\"",
            "    bar.txt \"BAR\"",
            "    baz/",
            "      boo.txt \"BOO\"",
            "  write/",
            "    foo.jar \"...\"",
            "    foo.txt \"FOO\"",
            "    top-level/",
            "      foo.txt \"FOO\"",
            "      bar.txt \"BAR\"",
            "      baz/",
            "    bar.txt \"BAR\"",
            "    baz/",
            "      boo.txt \"BOO\"",
            "    read/",
            "      bar.txt \"BAR\"",
            "  one-file/",
            "    bar.txt \"BAR\"",
            ""
            ),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testManifest() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/",
        "/",
        "  read/",
        "    foo/",
        "      bar.txt \"FOO\0BAR\"",
        "  write/");
    JarProcess h = new JarProcess();
    byte result = h.run(
        fs.getPath("/"), "c", "/write/j.jar",
        // A manifest with one entry, the pair (a, b).
        "2", "a", "b",
        "read", "1", "foo/bar.txt");
    assertEquals((byte) 0, result);

    result = h.run(
        fs.getPath("write"), "x", "/write/j.jar",
        "META-INF/*", "*", "alt///foo/*");
    assertEquals((byte) 0, result);

    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  read/",
            "    foo/",
            "      bar.txt \"FOO\0BAR\"",
            "  write/",
            "    j.jar \"...\"",
            "    META-INF/",
            "      MANIFEST.MF \"Manifest-Version: 1.0\\r\\na: b\\r\\n\\r\\n\"",
            "    alt/",
            "      foo/",
            "        bar.txt \"FOO\0BAR\"",
            "    foo/",
            ""
            ),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testEntryWithoutSize() throws IOException {
    fs = fileSystemFromAsciiArt("/", "/");
    {
      ZipOutputStream zout = new ZipOutputStream(
          fs.getPath("foo.zip").newOutputStream(StandardOpenOption.CREATE));
      ZipEntry e = new ZipEntry("e");
      zout.putNextEntry(e);
      zout.write("Hello, World!".getBytes(Charsets.UTF_8));
      zout.closeEntry();
      zout.close();
    }

    JarProcess.extractJar(fs.getPath("/"), "x", "foo.zip", "**");

    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  foo.zip \"...\"",
            "  e \"Hello, World!\"",
            ""
            ),
        fileSystemToAsciiArt(fs, 40));
  }
}
