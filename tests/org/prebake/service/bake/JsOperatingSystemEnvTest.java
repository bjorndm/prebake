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

import org.prebake.js.Executor;
import org.prebake.js.MembranableFunction;
import org.prebake.js.SimpleMembranableFunction;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.logging.Level;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;

import org.junit.After;
import org.junit.Test;

public final class JsOperatingSystemEnvTest extends PbTestCase {
  private FileSystem fs;

  @After public void tearDown() throws IOException {
    if (fs != null) {
      fs.close();
      fs = null;
    }
  }

  @Test public final void testMkdirs() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  tmp/",
            "    work/",
            "      baz/"));
    assertToolJsEquals(
        null, "os.mkdirs('foo/bar', 'baz', 'boo')");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  tmp/",
            "    work/",
            "      baz/",
            "      foo/",
            "        bar/",
            "      boo/",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testDirname() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  tmp/",
            "    work/"));
    assertToolJsEquals(null, "os.dirname('/')");
    assertToolJsEquals("/", "os.dirname('/foo')");
    assertToolJsEquals("/foo", "os.dirname('/foo/bar')");
    assertToolJsEquals("/foo", "os.dirname('/foo/bar/../baz')");
    assertToolJsEquals("/", "os.dirname('/foo/bar/..')");
    assertToolJsEquals("/foo/bar", "os.dirname('/foo/bar/baz')");
    assertToolJsEquals("foo", "os.dirname('foo/bar')");
    assertToolJsEquals("foo", "os.dirname('foo/bar/../baz')");
    assertToolJsEquals("", "os.dirname('foo/bar/..')");
    assertToolJsEquals("foo/bar", "os.dirname('foo/bar/baz')");
  }

  @Test public final void testBasename() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  tmp/",
            "    work/"));
    assertToolJsEquals("foo.txt", "os.basename('/foo/bar/foo.txt')");
    assertToolJsEquals("bar", "os.basename('/foo/bar/')");
    assertToolJsEquals("bar", "os.basename('/foo/bar')");
  }

  @Test public final void testJoinPaths() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  tmp/",
            "    work/"));
    assertToolJsEquals("", "os.joinPaths()");
    assertToolJsEquals("bar/foo.txt", "os.joinPaths('bar', 'foo.txt')");
    assertToolJsEquals("foo.txt", "os.joinPaths('bar', '..', 'foo.txt')");
    assertToolJsEquals("/a/b/foo.txt", "os.joinPaths('/a', 'b', 'foo.txt')");
  }

  private void assertToolJsEquals(@Nullable Object golden, String js) {
    Path dir = fs.getPath("/tmp/work");
    MembranableFunction execFn = new SimpleMembranableFunction(
        "stub exec", "exec", "process", "command, argv...") {
      public Object apply(Object[] args) {
        throw new UnsupportedOperationException();
      }
    };

    Executor.Output<?> result = Executor.Factory.createJsExecutor().run(
        Object.class, getLogger(Level.INFO), null,
        Executor.Input.builder(js, getName())
            .withActual("os", JsOperatingSystemEnv.makeJsInterface(dir, execFn))
            .build());
    if (result.exit != null) {
      Throwables.propagate(result.exit);
    }
    assertEquals(golden, result.result);
  }
}
