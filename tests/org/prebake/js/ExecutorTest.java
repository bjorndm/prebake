package org.prebake.js;

import org.prebake.fs.FilePerms;
import org.prebake.js.Executor.Input;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;

public class ExecutorTest extends PbTestCase {
  public final void testResult() throws Exception {
    assertResult(2.0, "1 + 1");
    assertResult("Hello, World!", "'Hello, World!'");
  }

  public final void testModuleIsDelayed() throws Exception {
    Executor.Output<?> out = doLoad(
        "typeof load('bar.js');",
        "bar.js", "1 + 1;");
    assertEquals("function", out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  public final void testFailedLoadRecovery() throws Exception {
    Executor.Output<?> out = doLoad(
        ""
        + "try {\n"
        + "  load('nosuchfile.js')();\n"
        + "} catch (ex) {\n"
        + "  ex.message;\n"
        + "}");
    assertEquals(
        "java.io.FileNotFoundException: /foo/nosuchfile.js",
        (String) out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  public final void testMalformedModule() throws Exception {
    Executor.Output<?> out = doLoad(
        ""
        + "try {\n"
        + "  load('../bar/malformed.js')();\n"
        + "} catch (ex) {\n"
        + "  ex.message;\n"
        + "}",
        "/bar/malformed.js", "NOT VALID JAVASCRIPT!!!");
    assertEquals(
        "missing ; before statement (/bar/malformed.js#1)",
        (String) out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  public final void testModuleResult() throws Exception {
    Executor.Output<?> out = doLoad(
        "load('baz.js')();",
        "baz.js", "1 + 1;");
    assertEquals(2.0, out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  public final void testDeterministicModuleUnused() throws Exception {
    Executor.Output<?> out = doLoad(
        "load('bar/baz.js')();",
        "bar/baz.js", "1 + 0 * load('boo.js');",
        "bar/boo.js", "Math.random()");
    assertTrue(Double.isNaN((Double) out.result));
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  public final void testDeterministicModuleUsed() throws Exception {
    Executor.Output<?> out = doLoad(
        "load('bar/baz.js')();",
        // boo.js loaded relative to bar/baz.js
        "bar/baz.js", "1 + 0 * load('boo.js')();",
        "bar/boo.js", "Math.random()");
    assertEquals(1.0, out.result);
    assertTrue(out.usedSourceOfKnownNondeterminism);
  }

  public final void testActuals() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder("x + 1", fs.getPath("/foo/" + getName() + ".js"))
        .withActuals(Collections.singletonMap("x", 1)).build());
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(2.0, output.result);

    // They shouldn't by default be visible to loaded modules.
    executor = Executor.Factory.createJsExecutor(Executor.Input.builder(
        "load('x')() + 1", fs.getPath("/foo/" + getName() + ".js"))
        .withActuals(Collections.singletonMap("x", 1))
        .build());
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        });
    assertEquals(3.0, output.result);

    // But a module can always elect to forward its scope.
    executor = Executor.Factory.createJsExecutor(Executor.Input.builder(
        "load('x')(this) + 1", fs.getPath("/foo/" + getName() + ".js"))
        .withActuals(Collections.singletonMap("x", 1))
        .build());
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        });
    assertEquals(2.0, output.result);

    // Or substitute its own.
    executor = Executor.Factory.createJsExecutor(Executor.Input.builder(
        "load('x')({ x: 3 }) + 1", fs.getPath("/foo/" + getName() + ".js"))
        .withActuals(Collections.singletonMap("x", 1))
        .build());
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        });
    assertEquals(4.0, output.result);

    // But in any case, changes to the module's scope don't affect the loader's.
    // But a module can always elect to forward its scope.
    executor = Executor.Factory.createJsExecutor(Executor.Input.builder(
        "load('x')(this) + x", fs.getPath("/foo/" + getName() + ".js"))
        .withActuals(Collections.singletonMap("x", 1))
        .build());
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("++x", p).build();
          }
        });
    assertEquals(3.0, output.result);
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

  public final void testConsole() throws Exception {
    // We use logging for the default locale, but the command line test runner
    // runs tests in the Turkish locale to flush out case folding problems.
    // We want to use the default locale for logging, but for these tests, we
    // switch to a consistent locale so that our test results are consistent
    // between an English IDE and our command line test environment when
    // formatting %d output below.
    Locale defaultLocale = Locale.getDefault();
    Locale.setDefault(Locale.ENGLISH);
    try {
      assertConsole("1 + 1");
      assertConsole("console.log(1 + 1)", "/testConsole.js:1:INFO: 2");
      assertConsole("console.log(5 / 2)", "/testConsole.js:1:INFO: 2.5");
      assertConsole(
          ""
          + "function f(x) {\n"
          + "  console.log('x=%d', x);\n"
          + "}\n"
          + "f(2)",
          "/testConsole.js:2:INFO: x=2");
      assertConsole(
          ""
          + "var x = 2.5;\n"
          + "console.log('x=%.2f', x)",
          "/testConsole.js:2:INFO: x=2.50");
      assertConsole(
          ""
          + "var x = 20;\n"
          + "console.log('x=%.1f x=%a x=%G x=%+.1e x=%d', x, x, x, x, x)",
          "/testConsole.js:2:INFO: x=20.0 x=0x1.4p4 x=20.0000 x=+2.0e+01 x=20");
      assertConsole("console.warn('foo')", "/testConsole.js:1:WARNING: foo");
      assertConsole("console.error('foo')", "/testConsole.js:1:SEVERE: foo");
      assertConsole(
          Level.FINE, "console.info('Hello, %s!', 'World')",
          "org.prebake.js.RhinoExecutor$LoadFn:FINE: Loading /testConsole.js",
          "org.prebake.js.RhinoExecutor$LoadFn:FINE: Done    /testConsole.js",
          "/testConsole.js:1:FINE: Hello, World!");
      assertConsole(
          "console.dir({ a: 1, b: 'Hello, World!', c: null, d: [1,2,3] })",
          ""
          + "/testConsole.js:1:INFO: \n"
          + "| Name | Value         |\n"
          + "| a    | 1             |\n"
          + "| b    | Hello, World! |\n"
          + "| c    | null          |\n"
          + "| d    | 1,2,3         |");
      assertConsole(
          ""
          + "console.group('foo');\n"
          + "console.log('hi');\n"
          + "console.groupEnd();",
          "/testConsole.js:1:INFO: Enter foo",
          "/testConsole.js:2:INFO:   hi",
          "/testConsole.js:3:INFO: Exit  foo");
      assertConsole(
          ""
          + "console.time('foo');\n"
          + "for (var i = 4; --i > 0;) { console.log(i); }\n"
          + "console.timeEnd('foo');",
          "/testConsole.js:2:INFO: 3",
          "/testConsole.js:2:INFO: 2",
          "/testConsole.js:2:INFO: 1",
          "/testConsole.js:3:INFO: Timer foo took <normalized>ns");

      // TEST console.assert, profile, profileEnd, etc.
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  public final void testJsWithBOM() throws Exception {
    assertResult(2.0, "\ufeff1 + 1");
  }

  public final void testGlobalsAvailable() throws Exception {
    Executor.Output<?> out;
    out = doLoad(
        "load('foo.js')({});",
        "foo.js", "typeof Function");
    assertEquals("function", out.result);
    out = doLoad(
        "load('foo.js')({});",
        "foo.js", "typeof this.Function");
    assertEquals("function", out.result);
  }

  public final void testModuleEnvironment() throws Exception {
    Executor.Output<?> out;
    out = doLoad(
        "load('foo.js')({ x: 2, y: 3 });",
        "foo.js", "x * y");
    assertEquals(6d, out.result);
    out = doLoad(
        "load('foo.js')({ x: 2, y: 3 });",
        "foo.js", "this.x * this.y");
    assertEquals(6d, out.result);
    out = doLoad(
        "load('fooDelegate.js')({ x: 2, y: 3 });",
        "fooDelegate.js", "load('foo.js')(this)",
        "foo.js", "this.x * this.y");
    assertEquals(6d, out.result);
  }

  public final void testToSource() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            "({ x: 1, y: function () { return 4; }, z: [2] })",
            fs.getPath("/foo/" + getName() + ".js"))
        .build());
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        });
    assertEquals(
        "({\"x\": 1.0, \"y\": (function() {\n  return 4;\n}), \"z\": [2.0]})",
        out.result.toSource());
  }

  public final void testToSourceFiltered1() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            ""
            + "({"
            + "  x: 1,"
            + "  y: [function () { return Math.abs(-1); },"
            + "      (function (i) { return function () { return i+1; } })(0)],"
            + "  z: [2]"
            + "})",
            fs.getPath("/foo/" + getName() + ".js")).build());
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        });
    assertEquals(
        ""
        + "({"
          + "\"x\": 1.0, "
          + "\"y\": [(function() {\n  return Math.abs(-1);\n}), null], "
          + "\"z\": [2.0]"
        + "})",
        out.result.toSource());
  }

  public final void testToSourceFiltered2() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            ""
            + "({"
            + "  x: 1,"
            + "  y: (function (i) { return function () { return i+1; } })(0),"
            + "  z: [2]"
            + "})",
            fs.getPath("/foo/" + getName() + ".js")).build());
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        });
    assertEquals("({\"x\": 1.0, \"z\": [2.0]})", out.result.toSource());
  }

  public final void testNoBase() throws Exception {
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder("typeof load", "" + getName()).build());
    Executor.Output<?> output = executor.run(
        Object.class, getLogger(Level.INFO), null);
    assertEquals("undefined", output.result);
  }

  public final void testLoaderVersionSkew() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor.Output<Number> result = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            "load('bar.js')() + load('bar.js')()",
            fs.getPath("/foo/" + getName()))
            .build())
        .run(Number.class, getLogger(Level.INFO), new Loader() {
          int count = 0;
          public Input load(Path p) throws IOException {
            if (!"/foo/bar.js".equals(p.toString())) {
              throw new FileNotFoundException(p.toString());
            }
            return Executor.Input.builder("" + (++count), p).build();
          }
        });
    // If load did not return the same content for the same path within an
    // executor run, then we would expect to see 3.
    assertEquals(2.0, result.result);
  }

  public final void testLoaderVersionSkewForFailingFile() throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor.Output<String> result = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            Joiner.on('\n').join(
                "var a, b;",
                "try {",
                "  a = load('bar.js')();",
                "} catch (e) {",
                "  a = e.toString();",
                "}",
                "try {",
                "  b = load('bar.js')();",
                "} catch (e) {",
                "  b = e.toString();",
                "}",
                "a + ' ; ' + b;"),
            fs.getPath("/foo/" + getName()))
            .build())
        .run(String.class, getLogger(Level.INFO), new Loader() {
          int count = 0;
          public Input load(Path p) throws IOException {
            // Will fail on the first load, and succeed on the second.
            if (count++ == 0) { throw new FileNotFoundException(p.toString()); }
            return Executor.Input.builder("1", p).build();
          }
        });
    // If load did not return the same content for the same path within an
    // executor run, then we would expect to see 3.
    assertEquals(
        "JavaException: java.io.FileNotFoundException: /foo/bar.js"
        + " ; JavaException: java.io.FileNotFoundException: /foo/bar.js",
        result.result);
  }

  @Test(timeout=10000, expected=Executor.ScriptTimeoutException.class)
  public final void testRunawayScripts() throws Exception {
    try {
      Executor.Factory.createJsExecutor(Executor.Input.builder(
          "var i = 0; while (1) { ++i; }", getName()).build())
          .run(Void.TYPE, getLogger(Level.INFO), null);
    } catch (Executor.ScriptTimeoutException ex) {
      // TODO: Neither ANT nor eclipse recognize expected exceptions
      return;
    }
  }

  public final void testActualInputs() throws Exception {
    Executor.Input srcX = Executor.Input.builder(
        "3", getName()).build();
    Executor.Input srcY = Executor.Input.builder(
        "4", getName()).build();
    Executor.Input srcTop = Executor.Input.builder("x + y", getName())
        .withActuals(
            ImmutableMap.<String, Object>builder()
            .put("x", srcX)
            .put("y", srcY)
            .build())
        .build();
    Executor.Output<?> output = Executor.Factory.createJsExecutor(srcTop)
        .run(Object.class, getLogger(Level.INFO), null);
    assertEquals(7d, output.result);
  }

  private void assertResult(Object result, String src) throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(src, fs.getPath("/foo/" + getName() + ".js"))
        .build());
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(src, result, output.result);
  }

  private void assertDeterministic(boolean deterministic, String src)
      throws Exception {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(src, fs.getPath("/foo/" + getName() + ".js"))
        .build());
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
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
      mkdirs(p.getParent());
      p.createFile(FilePerms.perms(0600, false));
      OutputStream out = p.newOutputStream(StandardOpenOption.CREATE);
      try {
        out.write(files[i + 1].getBytes(Charsets.UTF_8));
      } finally {
        out.close();
      }
    }
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(
            src, fs.getPath("/foo/" + getName() + ".js"))
        .build());
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            return Executor.Input.builder(
                new InputStreamReader(p.newInputStream(), Charsets.UTF_8), p)
                .build();
          }
        });
    return output;
  }

  private void assertConsole(String src, String... logStmts)
      throws Exception {
    assertConsole(Level.INFO, src, logStmts);
  }

  private void assertConsole(Level lvl, String src, String... logStmts)
      throws Exception {
    Logger logger = getLogger(lvl);
    final List<String> actualLog = getLog();
    actualLog.clear();

    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor(
        Executor.Input.builder(src, fs.getPath("/" + getName() + ".js"))
            .build());
    executor.run(
        Object.class,
        logger,
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        });
    assertEquals(
        src, Joiner.on('\n').join(logStmts),
        Joiner.on('\n').join(actualLog).replaceAll(
            "(:INFO: Timer \\w+ took )\\d+(ns)$", "$1<normalized>$2"));
  }
}
