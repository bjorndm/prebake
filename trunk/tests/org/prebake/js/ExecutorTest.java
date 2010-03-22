package org.prebake.js;

import org.mozilla.javascript.Undefined;
import org.prebake.service.StubFileSystemProvider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.logging.Logger;

import junit.framework.TestCase;

public class ExecutorTest extends TestCase {
  public final void testResult() throws Exception {
    assertResult(Undefined.instance, "1 + 1");
    assertResult(2.0, "return 1 + 1");
    assertResult("Hello, World!", "return 'Hello, World!'");
  }

  public final void testModuleIsDelayed() throws Exception {
    Executor.Output<?> out = doLoad(
        "return typeof load('bar.js');",
        "bar.js", "return 1 + 1;");
    assertEquals("function", out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
    assertEquals("[/foo/bar.js]", out.dynamicLoads.toString());
  }

  public final void testModuleResult() throws Exception {
    Executor.Output<?> out = doLoad(
        "return load('baz.js')();",
        "baz.js", "return 1 + 1;");
    assertEquals(2.0, out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
    assertEquals("[/foo/baz.js]", out.dynamicLoads.toString());
  }

  public final void testDeterministicModuleUnused() throws Exception {
    Executor.Output<?> out = doLoad(
        "return load('bar/baz.js')();",
        "bar/baz.js", "return 1 + 0 * load('boo.js');",
        "bar/boo.js", "return Math.random()");
    assertTrue(Double.isNaN((Double) out.result));
    assertFalse(out.usedSourceOfKnownNondeterminism);
    assertEquals(
        "[/foo/bar/baz.js, /foo/bar/boo.js]",
        out.dynamicLoads.toString());
  }

  public final void testDeterministicModuleUsed() throws Exception {
    Executor.Output<?> out = doLoad(
        "return load('bar/baz.js')();",
        // boo.js loaded relative to bar/baz.js
        "bar/baz.js", "return 1 + 0 * load('boo.js')();",
        "bar/boo.js", "return Math.random()");
    assertEquals(1.0, out.result);
    assertTrue(out.usedSourceOfKnownNondeterminism);
    assertEquals(
        "[/foo/bar/baz.js, /foo/bar/boo.js]",
        out.dynamicLoads.toString());
  }

  public final void testActuals() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(new Executor.Input(
        new StringReader("return x + 1"),
        fs.getPath("/foo/" + getName() + ".js")));
    Executor.Output<?> output = executor.run(
        Collections.singletonMap("x", 1),
        Object.class,
        Logger.getLogger(getName()),
        new Loader() {
          public Reader load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(2.0, output.result);
  }

  public final void testSourcesOfNonDeterminism() throws Exception {
    assertDeterministic(true, "1 + 1");
    assertDeterministic(false, "new Date()");
    assertDeterministic(true, "new Date(0)");
    assertDeterministic(true, "new Date(undefined)");
    assertDeterministic(false, "Date()");
    assertDeterministic(true, "Date(0)");
    assertDeterministic(false, "Date.call(null)");
    assertDeterministic(false, "Date.apply(null, [])");
    assertDeterministic(true, "Date.call(null, 0)");
    assertDeterministic(true, "Date.apply(null, [0])");
    assertDeterministic(true, "Date('1/1/1970')");
    assertDeterministic(true, "Date(undefined)");
    assertDeterministic(false, "Date.now()");
    assertDeterministic(false, "Date.now.call(null)");
    assertDeterministic(false, "Date.now.call(null)");
    assertDeterministic(false, "Date.now.apply(null, [])");
    try {
      assertDeterministic(false, "new Date.now()");
    } catch (Executor.AbnormalExitException ex) {
      // It's ok for it to be deterministic or to fail with new.
      Throwable th = ex.getCause();
      assertEquals(
          "TypeError: \"now\" is not a constructor."
          + " (/foo/testSourcesOfNonDeterminism.js#1)",
          th.getMessage());
    }
    assertDeterministic(false, "Math.random()");
    assertDeterministic(false, "Math.random.call(null)");
    assertDeterministic(false, "Math.random.apply(null, [])");
    try {
      assertDeterministic(false, "new Math.random()");
    } catch (Executor.AbnormalExitException ex) {
      // It's ok for it to be deterministic or to fail with new.
      Throwable th = ex.getCause();
      assertEquals(
          "TypeError: \"random\" is not a constructor."
          + " (/foo/testSourcesOfNonDeterminism.js#1)",
          th.getMessage());
    }
  }

  private void assertResult(Object result, String src) throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(new Executor.Input(
        new StringReader(src),
        fs.getPath("/foo/" + getName() + ".js")));
    Executor.Output<?> output = executor.run(
        Collections.<String, Object>emptyMap(),
        Object.class,
        Logger.getLogger(getName()),
        new Loader() {
          public Reader load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(src, result, output.result);
  }

  private void assertDeterministic(boolean deterministic, String src)
      throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(new Executor.Input(
        new StringReader(src),
        fs.getPath("/foo/" + getName() + ".js")));
    Executor.Output<?> output = executor.run(
        Collections.<String, Object>emptyMap(),
        Object.class,
        Logger.getLogger(getName()),
        new Loader() {
          public Reader load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(src, !deterministic, output.usedSourceOfKnownNondeterminism);
  }

  private Executor.Output<?> doLoad(String src, String... files)
      throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    for (int i = 0, n = files.length; i < n; i += 2) {
      Path p = fs.getPath(files[i]).toRealPath(true);
      p.getParent().createDirectory(PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rwx------")));
      p.createFile(PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rw-------")));
      OutputStream out = p.newOutputStream(StandardOpenOption.CREATE);
      try {
        out.write(files[i + 1].getBytes("UTF-8"));
      } finally {
        out.close();
      }
    }
    Executor executor = Executor.Factory.createJsExecutor(new Executor.Input(
        new StringReader(src),
        fs.getPath("/foo/" + getName() + ".js")));
    Executor.Output<?> output = executor.run(
        Collections.<String, Object>emptyMap(),
        Object.class,
        Logger.getLogger(getName()),
        new Loader() {
          public Reader load(Path p) throws IOException {
            return new InputStreamReader(p.newInputStream(), "UTF-8");
          }
        });
    return output;
  }
}
