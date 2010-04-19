package org.prebake.js;

import org.prebake.core.Documentation;
import org.prebake.fs.FilePerms;
import org.prebake.js.Executor.Input;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;

public class ExecutorTest extends PbTestCase {
  @Test public final void testResult() {
    assertResult(2.0, "1 + 1");
    assertResult("Hello, World!", "'Hello, World!'");
  }

  @Test public final void testModuleIsDelayed() throws IOException {
    Executor.Output<?> out = doLoad(
        "typeof load('bar.js');",
        "bar.js", "1 + 1;");
    assertEquals("function", out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testFailedLoadRecovery() throws IOException {
    Executor.Output<?> out = doLoad(
        ""
        + "try {\n"
        + "  load('nosuchfile.js')();\n"
        + "} catch (ex) {\n"
        + "  ex.message;\n"
        + "}");
    assertEquals(
        "java.io.FileNotFoundException: /foo/nosuchfile.js",
        out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testMalformedModule() throws IOException {
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
        out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testModuleResult() throws IOException {
    Executor.Output<?> out = doLoad(
        "load('baz.js')();",
        "baz.js", "1 + 1;");
    assertEquals(2.0, out.result);
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testDeterministicModuleUnused() throws IOException {
    Executor.Output<?> out = doLoad(
        "load('bar/baz.js')({ load: load });",
        "bar/baz.js", "1 + 0 * load('boo.js');",
        "bar/boo.js", "Math.random()");
    assertTrue(Double.isNaN((Double) out.result));
    assertFalse(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testDeterministicModuleUsed() throws IOException {
    Executor.Output<?> out = doLoad(
        "load('bar/baz.js')({ load: load });",
        // boo.js loaded relative to bar/baz.js
        "bar/baz.js", "1 + 0 * load('boo.js')();",
        "bar/boo.js", "Math.random()");
    assertEquals(1.0, out.result);
    assertTrue(out.usedSourceOfKnownNondeterminism);
  }

  @Test public final void testActuals() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        }, Executor.Input.builder(
            "x + 1", fs.getPath("/foo/" + getName() + ".js"))
            .withActuals(Collections.singletonMap("x", 1)).build());
    assertEquals(2.0, output.result);

    // They shouldn't by default be visible to loaded modules.
    executor = Executor.Factory.createJsExecutor();
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        }, Executor.Input.builder(
            "load('x')() + 1", fs.getPath("/foo/" + getName() + ".js"))
            .withActuals(Collections.singletonMap("x", 1))
            .build());
    assertEquals(3.0, output.result);

    // But a module can always elect to forward its scope.
    executor = Executor.Factory.createJsExecutor();
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        }, Executor.Input.builder(
            "load('x')(this) + 1", fs.getPath("/foo/" + getName() + ".js"))
            .withActuals(Collections.singletonMap("x", 1))
            .build());
    assertEquals(2.0, output.result);

    // Or substitute its own.
    executor = Executor.Factory.createJsExecutor();
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("typeof x !== 'undefined' ? x : 2", p)
                .build();
          }
        }, Executor.Input.builder(
            "load('x')({ x: 3 }) + 1", fs.getPath("/foo/" + getName() + ".js"))
            .withActuals(Collections.singletonMap("x", 1))
            .build());
    assertEquals(4.0, output.result);

    // But in any case, changes to the module's scope don't affect the loader's.
    // But a module can always elect to forward its scope.
    executor = Executor.Factory.createJsExecutor();
    output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) {
            return Executor.Input.builder("++x", p).build();
          }
        }, Executor.Input.builder(
            "load('x')(this) + x", fs.getPath("/foo/" + getName() + ".js"))
            .withActuals(Collections.singletonMap("x", 1))
            .build());
    assertEquals(3.0, output.result);
  }

  @Test public final void testSourcesOfNonDeterminism() {
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
    assertDeterministic(
        false, "new Date.now()",
        "TypeError: \"now\" is not a constructor. (/foo/ExecutorTest.js#1)");
    assertDeterministic(false, "Math.random()");
    assertDeterministic(false, "Math.random.call(null)");
    assertDeterministic(false, "Math.random.apply(null, [])");
    assertDeterministic(
        false, "new Math.random()",
        "TypeError: \"random\" is not a constructor. (/foo/ExecutorTest.js#1)");
  }

  @Test public final void testConsole() {
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
      assertConsole("console.log(1 + 1)", "/ExecutorTest.js:1:INFO: 2");
      assertConsole("console.log(5 / 2)", "/ExecutorTest.js:1:INFO: 2.5");
      assertConsole(
          ""
          + "function f(x) {\n"
          + "  console.log('x=%d', x);\n"
          + "}\n"
          + "f(2)",
          "/ExecutorTest.js:2:INFO: x=2");
      assertConsole(
          ""
          + "var x = 2.5;\n"
          + "console.log('x=%.2f', x)",
          "/ExecutorTest.js:2:INFO: x=2.50");
      assertConsole(
          ""
          + "var x = 20;\n"
          + "console.log('x=%.1f x=%a x=%G x=%+.1e x=%d', x, x, x, x, x)",
          "/ExecutorTest.js:2:INFO: x=20.0 x=0x1.4p4 x=20.0000 x=+2.0e+01 x=20"
          );
      assertConsole("console.warn('foo')", "/ExecutorTest.js:1:WARNING: foo");
      assertConsole("console.error('foo')", "/ExecutorTest.js:1:SEVERE: foo");
      assertConsole(
          Level.FINE, "console.info('Hello, %s!', 'World')",
          "FINE: Loading /ExecutorTest.js",
          "FINE: Done    /ExecutorTest.js",
          "/ExecutorTest.js:1:FINE: Hello, World!");
      assertConsole(
          "console.dir({ a: 1, b: 'Hello, World!', c: null, d: [1,2,3] })",
          ""
          + "/ExecutorTest.js:1:INFO: \n"
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
          "/ExecutorTest.js:1:INFO: Enter foo",
          "/ExecutorTest.js:2:INFO:   hi",
          "/ExecutorTest.js:3:INFO: Exit  foo");
      assertConsole(
          ""
          + "console.time('foo');\n"
          + "for (var i = 4; --i > 0;) { console.log(i); }\n"
          + "console.timeEnd('foo');",
          "/ExecutorTest.js:2:INFO: 3",
          "/ExecutorTest.js:2:INFO: 2",
          "/ExecutorTest.js:2:INFO: 1",
          "/ExecutorTest.js:3:INFO: Timer foo took <normalized>ns");

      // TEST console.assert, profile, profileEnd, etc.
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test public final void testJsWithBOM() {
    assertResult(2.0, "\ufeff1 + 1");
  }

  @Test public final void testGlobalsAvailable() throws IOException {
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

  @Test public final void testModuleEnvironment() throws IOException {
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
        "load('fooDelegate.js')({ x: 2, y: 3, load: load });",
        "fooDelegate.js", "load('foo.js')(this)",
        "foo.js", "this.x * this.y");
    assertEquals(6d, out.result);
  }

  @Test public final void testToSource() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        }, Executor.Input.builder(
            "({ x: 1, y: function () { return 4; }, z: [2] })",
            fs.getPath("/foo/" + getName() + ".js"))
            .build());
    assertEquals(
        "({\"x\": 1.0, \"y\": (function() {\n  return 4;\n}), \"z\": [2.0]})",
        out.result.toSource());
  }

  @Test public final void testToSourceFiltered1() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        }, Executor.Input.builder(
            ""
            + "({"
            + "  x: 1,"
            + "  y: [function () { return Math.abs(-1); },"
            + "      (function (i) { return function () { return i+1; } })(0)],"
            + "  z: [2]"
            + "})",
            fs.getPath("/foo/" + getName() + ".js")).build());
    assertEquals(
        ""
        + "({"
          + "\"x\": 1.0, "
          + "\"y\": [(function() {\n  return Math.abs(-1);\n}), null], "
          + "\"z\": [2.0]"
        + "})",
        out.result.toSource());
  }

  @Test public final void testToSourceFiltered2() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<YSON> out = executor.run(
        YSON.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Should not laod");
          }
        }, Executor.Input.builder(
            ""
            + "({"
            + "  x: 1,"
            + "  y: (function (i) { return function () { return i+1; } })(0),"
            + "  z: [2]"
            + "})",
            fs.getPath("/foo/" + getName() + ".js")).build());
    assertEquals("({\"x\": 1.0, \"z\": [2.0]})", out.result.toSource());
  }

  @Test public final void testNoBase() {
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<?> output = executor.run(
        Object.class, getLogger(Level.INFO), null,
        Executor.Input.builder("typeof load", "" + getName()).build());
    assertEquals("undefined", output.result);
  }

  @Test public final void testLoaderVersionSkew() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor.Output<Number> result = Executor.Factory.createJsExecutor()
        .run(Number.class, getLogger(Level.INFO), new Loader() {
          int count = 0;
          public Input load(Path p) throws IOException {
            if (!"/foo/bar.js".equals(p.toString())) {
              throw new FileNotFoundException(p.toString());
            }
            return Executor.Input.builder("" + (++count), p).build();
          }
        }, Executor.Input.builder(
            "load('bar.js')() + load('bar.js')()",
            fs.getPath("/foo/" + getName()))
            .build());
    // If load did not return the same content for the same path within an
    // executor run, then we would expect to see 3.
    assertEquals(2.0, result.result);
  }

  @Test public final void testLoaderVersionSkewForFailingFile() {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor.Output<String> result = Executor.Factory.createJsExecutor()
        .run(String.class, getLogger(Level.INFO), new Loader() {
          int count = 0;
          public Input load(Path p) throws IOException {
            // Will fail on the first load, and succeed on the second.
            if (count++ == 0) { throw new FileNotFoundException(p.toString()); }
            return Executor.Input.builder("1", p).build();
          }
        }, Executor.Input.builder(
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
            .build());
    // If load did not return the same content for the same path within an
    // executor run, then we would expect to see 3.
    assertEquals(
        "JavaException: java.io.FileNotFoundException: /foo/bar.js"
        + " ; JavaException: java.io.FileNotFoundException: /foo/bar.js",
        result.result);
  }

  @Test(timeout=10000, expected=Executor.ScriptTimeoutException.class)
  public final void testRunawayScripts() {
    Executor.Factory.createJsExecutor()
        .run(Void.TYPE, getLogger(Level.INFO), null,
             Executor.Input.builder("var i = 0; while (1) { ++i; }", getName())
                 .build());
  }

  @Test public final void testActualInputs() {
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
    Executor.Output<?> output = Executor.Factory.createJsExecutor()
        .run(Object.class, getLogger(Level.INFO), null, srcTop);
    assertEquals(7d, output.result);
  }

  @Test public final void testHelpFunction() {
    assertHelpOutput("");
    assertHelpOutput("null");
    assertHelpOutput("{}");
    assertHelpOutput(
        "{ help_: 'Howdy' }",
        "INFO: Help: Howdy");
    assertHelpOutput(
        "{ help_: { detail: 'Howdy' } }",
        "INFO: Help: Howdy");
    assertHelpOutput(
        "{ help_: { summary: 'brief', detail: 'Howdy' } }",
        "INFO: Help: brief\nHowdy");
    assertHelpOutput(
        "{ help_: { summary: 'brief', detail: 'brief. longer' } }",
        "INFO: Help: brief. longer");
    assertHelpOutput(
        "{ help_: { detail: 'description', contact: 'foo@bar.baz' } }",
        "INFO: Help: description\nContact: foo@bar.baz");
  }

  @Test public final void testLambdasAreFunctions() {
    assertEquals("function", runWithLambdaFoo("typeof foo").result);
  }

  @Test public final void testLambdasAreNamed() {
    assertEquals("Foo", runWithLambdaFoo("foo.name").result);
  }

  @Test public final void testLambdasAreInvokable() {
    assertEquals("foo1.02.0", runWithLambdaFoo("foo(1, 2)").result);
  }

  @Test public final void testLambdasAreApplyable() {
    assertEquals("foo1.02.0", runWithLambdaFoo("foo.apply({}, [1, 2])").result);
  }

  @Test public final void testLambdasAreCallable() {
    assertEquals("foo1.02.0", runWithLambdaFoo("foo.call({}, 1, 2)").result);
  }

  private Executor.Output<String> runWithLambdaFoo(String js) {
    Executor execer = Executor.Factory.createJsExecutor();
    return execer.run(
        String.class, getLogger(Level.INFO), null,
        Executor.Input.builder(js, getName())
            .withActuals(ImmutableMap.of(
                "foo", execer.toFunction(new Function<Object[], Object>() {
                  public Object apply(Object[] arguments) {
                    StringBuilder sb = new StringBuilder("foo");
                    for (Object argument : arguments) { sb.append(argument); }
                    return sb.toString();
                  }
                }, "Foo", new Documentation("hi", "howdy", null)))).build());
  }

  private void assertHelpOutput(String actuals, String... log) {
    Logger logger = getLogger(Level.INFO);
    getLog().clear();
    String result = Executor.Factory.createJsExecutor()
        .run(String.class, logger, null,
             (Executor.Input.builder("typeof help(" + actuals + ")", getName())
              .build()))
        .result;
    assertEquals("undefined", result);
    assertEquals(
        Arrays.asList(log),
        getLog());
  }

  private void assertResult(Object result, String src) {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        }, Executor.Input.builder(
            src, fs.getPath("/foo/" + getName() + ".js"))
            .build());
    assertEquals(src, result, output.result);
  }

  private void assertDeterministic(boolean deterministic, String src) {
    assertDeterministic(deterministic, src, null);
  }

  private void assertDeterministic(
      boolean deterministic, String src, String errorMessage) {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        }, Executor.Input.builder(
            src, fs.getPath("/foo/" + getName() + ".js")).build());
    // If no optional error message specified, the JS should not fail
    if (errorMessage == null) { assertNull(output.exit); }
    if (output.exit == null) {
      assertEquals(src, !deterministic, output.usedSourceOfKnownNondeterminism);
    } else {
      assertEquals(errorMessage, output.exit.getCause().getMessage());
    }
  }

  private Executor.Output<?> doLoad(String src, String... files)
      throws IOException {
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
    Executor executor = Executor.Factory.createJsExecutor();
    Executor.Output<?> output = executor.run(
        Object.class,
        getLogger(Level.INFO),
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            return Executor.Input.builder(
                new InputStreamReader(p.newInputStream(), Charsets.UTF_8), p)
                .build();
          }
        }, Executor.Input.builder(
            src, fs.getPath("/foo/" + getName() + ".js"))
            .build());
    assertNull(output.exit);
    return output;
  }

  private void assertConsole(String src, String... logStmts) {
    assertConsole(Level.INFO, src, logStmts);
  }

  private void assertConsole(Level lvl, String src, String... logStmts) {
    Logger logger = getLogger(lvl);
    final List<String> actualLog = getLog();
    actualLog.clear();

    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/foo"));
    Executor executor = Executor.Factory.createJsExecutor();
    executor.run(
        Object.class,
        logger,
        new Loader() {
          public Executor.Input load(Path p) throws IOException {
            throw new IOException("Not testing load");
          }
        }, Executor.Input.builder(
            src, fs.getPath("/" + getName() + ".js"))
            .build());
    assertEquals(
        src, Joiner.on('\n').join(logStmts),
        Joiner.on('\n').join(actualLog).replaceAll(
            "(:INFO: Timer \\w+ took )\\d+(ns)$", "$1<normalized>$2"));
  }
}
