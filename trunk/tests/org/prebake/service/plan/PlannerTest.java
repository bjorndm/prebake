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

package org.prebake.service.plan;

import org.prebake.core.ArtifactListener;
import org.prebake.core.BoundName;
import org.prebake.core.Documentation;
import org.prebake.core.GlobRelation;
import org.prebake.core.GlobSet;
import org.prebake.fs.StubFileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.MobileFunction;
import org.prebake.service.HighLevelLog;
import org.prebake.service.Logs;
import org.prebake.service.TestLogHydra;
import org.prebake.service.tools.ToolContent;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;
import org.prebake.util.Clock;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubScheduledExecutorService;
import org.prebake.util.TestClock;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ValueFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

public class PlannerTest extends PbTestCase {
  // TODO: HIGH.  Let checker infer outputs from inputs, so that
  //   tools.gcc('**.cc') -> {
  //     tool: 'gcc', inputs: ['**.c'], outputs: ['**.o'], options: {'-c': true}
  //   }
  // while
  //   tools.gcc('**.cc', 'main.so') -> {
  //     tool: 'gcc', inputs: ['**.c'], outputs: ['main.so'], options: ...
  //   }

  private Tester test;

  @Before
  public void initTest() {
    test = new Tester();
  }

  @After
  public void closeTest() throws IOException {
    test.close();
  }

  @Test public final void testToolsAvailable() throws IOException {
    String planFile = "for(var k in tools){\nconsole.log(k);\n}\n({})";
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan.js " + JsonSink.stringify(planFile))
        .withTools(
            tool("gcc"),
            tool("javac"),
            tool("cp"))
        .withPlanFiles("plan.js")
        .expectLog(
            "/cwd/plan.js:2:INFO: gcc",
            "/cwd/plan.js:2:INFO: javac",
            "/cwd/plan.js:2:INFO: cp",
            "INFO: Plan file plan.js is up to date")
        .run();
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    plan.js " + JsonSink.stringify(planFile),
            "  logs/",
            "    plan+2e+js.plan.log "
                 + JsonSink.stringify(Joiner.on('\n').join(
                     "INFO:gcc",
                     "INFO:javac",
                     "INFO:cp",
                     "INFO:Plan file plan.js is up to date",
                     "")),
            ""),
        fileSystemToAsciiArt(test.fs, 80));
  }

  @Test public final void testSimpleProduct() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan1.js \"({p:{actions:[tools.gcc(['**.c'], ['**.o'])]}})\"")
        .withTools(tool("gcc"))
        .withPlanFiles("plan1.js")
        .expectProduct("p", action("gcc", "**.c", "**.o"))
        .expectLog("INFO: Plan file plan1.js is up to date")
        .run();
  }

  @Test public final void testGlobbing() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan1.js \"({p:tools.gcc('**.{c,cc}', '**.o')})\"")
        .withTools(tool("gcc"))
        .withPlanFiles("plan1.js")
        .expectProduct(
            "p",
            action("gcc", Arrays.asList("**.c", "**.cc"), Arrays.asList("**.o"))
            )
        .expectLog("INFO: Plan file plan1.js is up to date")
        .run();
  }

  @Test public final void testBadToolReturnValue() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan1.js \"({ foo: null })\"",
            ("    plan2.js \"({\\n"
                 + "  myProduct: {\\n"
                 + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
                 + "  }\\n"
                 + "})\""))
        .withTools(tool("myTool"))
        .withPlanFiles("plan1.js", "plan2.js")
        .expectProduct("myProduct", action("myTool", "**.foo", "**.bar"))
        .expectLog(
            "WARNING: Expected {\"inputs\":['*.glob', ...],...}, not null",
            "SEVERE: Failed to update plan plan1.js",
            "INFO: Plan file plan2.js is up to date")
        .run();
  }

  @Test public final void testToolFileThrows() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
                  + "  myProduct: {\\n"
                  + "    inputs: [ '**.foo' ],\\n"
                  + "    outputs: [ '**.bar' ],\\n"
                  + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
                  + "  }\\n"
                  + "})\""),
             "    plan2.js \"throw {}\"")
        .withTools(tool("myTool"))
        .withPlanFiles("plan1.js", "plan2.js")
        .expectProduct("myProduct", action("myTool", "**.foo", "**.bar"))
        .expectLog(
            "INFO: Plan file plan1.js is up to date",
            "WARNING: Error executing plan plan2.js\n"
            + "org.mozilla.javascript.JavaScriptException:"
            + " [object Object] (/cwd/plan2.js#1)",
            "SEVERE: Failed to update plan plan2.js")
        .run();
  }

  @Test public final void testMalformedToolFile() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
                 + "  myProduct: {\\n"
                 + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
                 + "  }\\n"
                 + "})\""),
             "    plan2.js \"({ anotherTool: \"")  // JS syntax error
        .withTools(tool("myTool"))
        .withPlanFiles("plan1.js", "plan2.js")
        .expectProduct("myProduct", action("myTool", "**.foo", "**.bar"))
        .expectLog(
            "INFO: Plan file plan1.js is up to date",
            "WARNING: Failed to execute plan plan2.js\n"
            + "org.prebake.js.Executor$AbnormalExitException:"
            + " org.mozilla.javascript.EvaluatorException:"
            + " Unexpected end of file (/cwd/plan2.js#1)",
            "SEVERE: Failed to update plan plan2.js")
        .run();
  }

  @Test public final void testToolHelp() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan1.js \"help(tools.myTool); ({})\"")
        .withTools(tool(
            "myTool", new Documentation(null, "A very useful tool", null)))
        .withPlanFiles("plan1.js")
        .expectLog(
            "/cwd/plan1.js:1:INFO: Help: myTool\nA very useful tool",
            "INFO: Plan file plan1.js is up to date")
        .run();
  }

  @Test public final void testSanityChecker() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
                 + "  aProduct: {\\n"
                 + "    inputs: [ '**.a' ],\\n"
                 + "    outputs: [ '**.b' ],\\n"
                 + "    actions: [ tools.munge(['**.a'], ['**.b']) ]\\n"
                 + "  }\\n"
                 + "})\""))
        .withTools(tool(
            "munge",
            "function () { console.log('Looks spiffy!  Happy munging!') }",
            new Documentation(null, "munges stuff", null)))
        .withPlanFiles("plan1.js")
        .expectProduct("aProduct", action("munge", "**.a", "**.b"))
        .expectLog(
            "INFO: Looks spiffy!  Happy munging!",
            "INFO: Plan file plan1.js is up to date")
        .run();
  }

  @Test public final void testMalformedGlob() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
                 + "  aProduct: {\\n"
                 + "    inputs: [ '//*.*' ],\\n"
                 + "    outputs: [ '**.b' ],\\n"
                 + "    actions: [ tools.munge(['**.a'], ['**.b']) ]\\n"
                 + "  }\\n"
                 + "})\""))
        .withTools(tool("munge"))
        .withPlanFiles("plan1.js")
        .expectLog(
            "WARNING: Bad glob: '//*.*'",
            "SEVERE: Failed to update plan plan1.js")
        .run();
  }

  @Test public final void testMissingPlanFile() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
                 + "  aProduct: {\\n"
                 + "    actions: [ tools.munge(['**.a'], ['**.b']) ]\\n"
                 + "  }\\n"
                 + "})\""))
        .withTools(tool("munge"))
        .withPlanFiles("plan1.js", "plan2.js")  // plan2 does not exists
        .expectLog(
            "INFO: Plan file plan1.js is up to date",
            "WARNING: Missing plan plan2.js",
            "java.io.FileNotFoundException: /cwd/plan2.js",
            "SEVERE: Failed to update plan plan2.js")
        .expectProduct("aProduct", action("munge", "**.a", "**.b"))
        .run();
  }

  @Test public final void testPlanFileLoads() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan.js \"load('../bar/foo.js')({tools: tools, load: load})\"",
            "  bar/",
            "    foo.js \"load('baz.js')(this)\"",
            "    baz.js \"({p:{actions:[tools.gcc(['**.c'],['**.o'])]}})\"")
        .withTools(tool("gcc"))
        .withPlanFiles("plan.js")
        .expectProduct("p", action("gcc", "**.c", "**.o"))
        .expectLog("INFO: Plan file plan.js is up to date")
        .run();
  }

  @Test public final void testPlanFileLoadMissingFile() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    plan.js \"load('../bar/foo.js')(this)\"")
        .withTools(tool("gcc"))
        .withPlanFiles("plan.js")
        .expectLog(
            "WARNING: Failed to execute plan plan.js",
            ("org.prebake.js.Executor$AbnormalExitException"
             + ": org.mozilla.javascript.WrappedException"
             + ": Wrapped java.io.FileNotFoundException"
             + ": /bar/foo.js (/cwd/plan.js#1)"),
            "SEVERE: Failed to update plan plan.js")
        .run();
  }

  @Test public final void testPlanFileTimeout() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    p.js \"while (true);\"",
            "    q.js \"({p:{actions:[tools.cp('**.foo','**.bar')]}})\"")
        .withTools(tool("cp"))
        .withPlanFiles("p.js", "q.js")
        .expectProduct("p", action("cp", "**.foo", "**.bar"))
        .expectLog(
            "WARNING: Error executing plan p.js",
            Executor.ScriptTimeoutException.class.getName() + ": ",
            "SEVERE: Failed to update plan p.js",
            "INFO: Plan file q.js is up to date")
        .run();
  }

  @Test public final void testMaskingProductNames() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            ("    p1.js \""
                + "({p:{actions:[tools.gcc(['**.c'], ['**.o'], {'-c':true})]},"
                + "  q:{actions:[tools.gcc(['**.o'], ['**.lib'])]}})\""),
            ("    p2.js \"({p:{actions:[tools.gcc(['**.c'], ['**.lib'])]}})\""))
        .withTools(tool("gcc"))
        .withPlanFiles("p1.js", "p2.js")
        .expectProduct("q", action("gcc", "**.o", "**.lib"))
        .expectLog(
            "INFO: Plan file p1.js is up to date",
            "INFO: Plan file p2.js is up to date",
            "WARNING: Duplicate product p in p2.js and p1.js")
        .run();
  }

  @Test public final void testToolFileUpdated() throws IOException {
    test.withFileSystem(
            "/",
            "  cwd/",
            "    p.js \"console.log('p'), {p:tools.cp('**.a','**.w')}\"",
            "    q.js \"console.log('q'), {q:tools.cp('**.b','**.x')}\"",
            "    r.js \"console.log('r'), load('../cwd/bar/foo.js')(this)\"",
            "    s.js \"console.log('s'), load('./bar/baz.js')(this)\"",
            "    bar/",
            "      foo.js \"console.log('foo'), {r:tools.cp('**.c','**.y')}\"",
            "      baz.js \"console.log('baz'), {s:tools.cp('**.d','**.z')}\"")
        .withTools(tool("cp"))
        .withPlanFiles("p.js", "q.js", "r.js", "s.js")
        .updateFiles("bar/foo.js", "bar/baz.js")
        .expectProduct("p", action("cp", "**.a", "**.w"))
        .expectProduct("q", action("cp", "**.b", "**.x"))
        .expectProduct("r", action("cp", "**.c", "**.y"))
        .expectProduct("s", action("cp", "**.d", "**.z"))
        .expectLog(
            "/cwd/p.js:1:INFO: p",
            "INFO: Plan file p.js is up to date",
            "/cwd/q.js:1:INFO: q",
            "INFO: Plan file q.js is up to date",
            "/cwd/r.js:1:INFO: r",
            "/cwd/bar/foo.js:1:INFO: foo",
            "INFO: Plan file r.js is up to date",
            "/cwd/s.js:1:INFO: s",
            "/cwd/bar/baz.js:1:INFO: baz",
            "INFO: Plan file s.js is up to date")
        .run()
        .writeFile("r.js", "console.log('r'), {r:tools.cp('**/*.c','**/*.y')}")
        .writeFile("bar/baz.js",
                   "console.log('baz'), {t:tools.cp('**/*.d','**/*.z')}")
        .updateFiles("r.js", "/cwd/bar/baz.js")
        .expectLog(
            "/cwd/r.js:1:INFO: r",
            "INFO: Plan file r.js is up to date",
            "/cwd/s.js:1:INFO: s",
            "/cwd/bar/baz.js:1:INFO: baz",
            "INFO: Plan file s.js is up to date")
        .expectProduct("p", action("cp", "**.a", "**.w"))
        .expectProduct("q", action("cp", "**.b", "**.x"))
        .expectProduct("r", action("cp", "**/*.c", "**/*.y"))
        .expectProduct("t", action("cp", "**/*.d", "**/*.z"))
        .run()
        .deleteFile("/cwd/q.js")
        .updateFiles("/cwd/q.js", "/cwd/r.js" /* no change */)
        .expectLog(
            "WARNING: Missing plan q.js",
            "java.io.FileNotFoundException: /cwd/q.js",
            "SEVERE: Failed to update plan q.js")
        .expectProduct("p", action("cp", "**.a", "**.w"))
        .expectProduct("r", action("cp", "**/*.c", "**/*.y"))
        .expectProduct("t", action("cp", "**/*.d", "**/*.z"))
        .run();
  }

  @Test public final void testProductWithBadName1() throws IOException {
    String planFile = (
        ""
        + "({"
        + "  'b#d':tools.gcc('**.cc', '**.o'),"
        + "  p    :tools.gcc('**.c', '**.o')"
        + "});");
    test.withFileSystem(
            "/",
            "  cwd/",
            "    Bakefile.js " + JsonSink.stringify(planFile))
        .withTools(tool("gcc"))
        .withPlanFiles("Bakefile.js")
        .expectLog(
            "WARNING: Expected a bound name but was \"b#d\"",
            "SEVERE: Failed to update plan Bakefile.js")
        .run();
  }

  // TODO: test plan file that doesn't exist, but then is created.

  private final class Tester {
    private FileSystem fs;
    private final ImmutableList.Builder<Future<ToolSignature>> sigs
        = ImmutableList.builder();
    private final List<String> goldenLog = Lists.newArrayList();
    private final Map<String, Product> goldenProds = Maps.newLinkedHashMap();
    private StubFileVersioner files;
    private ToolProvider toolbox;
    private Planner planner;

    public Tester withFileSystem(String... asciiArt) throws IOException {
      assertNull(fs);
      Logger logger = getLogger(Level.INFO);
      fs = fileSystemFromAsciiArt("/cwd", Joiner.on('\n').join(asciiArt));
      files = new StubFileVersioner(
          fs.getPath("/cwd"), Predicates.<Path>alwaysTrue(), logger);
      toolbox = new ToolProvider() {
        private boolean isClosed;
        public List<Future<ToolSignature>> getAvailableToolSignatures() {
          if (isClosed) { throw new IllegalStateException(); }
          return sigs.build();
        }
        public ToolContent getTool(String toolName) {
          throw new Error("Unsupported by " + PlannerTest.class);
        }
        public void close() { isClosed = true; }
      };
      mkdirs(fs.getPath("/logs"));
      return this;
    }

    public Tester withTools(ToolSignature... sigs) {
      for (ToolSignature sig : sigs) {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(sig);
        this.sigs.add(f);
      }
      return this;
    }

    public Tester withPlanFiles(String... planFiles) {
      ImmutableList.Builder<Path> b = ImmutableList.builder();
      for (String planFile : planFiles) {
        b.add(fs.getPath(planFile));
      }
      ScheduledExecutorService execer = new StubScheduledExecutorService();
      Logger logger = getLogger(Level.INFO);
      List<Path> planFileList = b.build();
      files.updateFiles(planFileList);
      Clock clock = new TestClock();
      TestLogHydra logHydra = new TestLogHydra(
          logger, fs.getPath("/logs"), clock);
      logHydra.install();
      Logs logs = new Logs(new HighLevelLog(clock), logger, logHydra);
      planner = new Planner(
          files, getCommonJsEnv(), toolbox, planFileList, logs,
          ArtifactListener.Factory.<Product>noop(), execer);
      return this;
    }

    public Tester expectLog(String... log) {
      goldenLog.addAll(Arrays.asList(log));
      return this;
    }

    public Tester expectProducts(Product... prods) {
      for (Product p : prods) { goldenProds.put(p.name.ident, p); }
      return this;
    }

    public Tester expectProduct(String name, Action a) {
      return expectProduct(name, a.inputs, a.outputs, a);
    }

    public Tester expectProduct(
        String name, GlobSet inputs, GlobSet outputs, Action... actions) {
      return expectProducts(new Product(
          BoundName.fromString(name), null,
          new GlobRelation(inputs, outputs), Arrays.asList(actions),
          false, null, fs.getPath("/cwd")));
    }

    public Tester writeFile(String path, String content) throws IOException {
      Writer out = new OutputStreamWriter(
          fs.getPath(path)
              .newOutputStream(StandardOpenOption.TRUNCATE_EXISTING),
          Charsets.UTF_8);
      try {
        out.write(content);
      } finally {
        out.close();
      }
      return this;
    }

    public Tester deleteFile(String path) throws IOException {
      fs.getPath(path).delete();
      return this;
    }

    public Tester updateFiles(String... filePaths) {
      int n = filePaths.length;
      Path[] paths = new Path[n];
      for (int i = n; --i >= 0;) { paths[i] = fs.getPath(filePaths[i]); }
      files.updateFiles(Arrays.asList(paths));
      return this;
    }

    public Tester run() {
      Map<BoundName, Product> products = planner.getProducts();
      assertEquals(goldenProds.toString(), products.toString());
      assertEquals(
          Joiner.on('\n').join(goldenLog), Joiner.on('\n').join(getLog()));
      getLog().clear();
      this.goldenProds.clear();
      this.goldenLog.clear();
      return this;
    }

    public void close() throws IOException {
      planner.close();
      toolbox.close();
      files.close();
      fs.close();
    }
  }

  private static ToolSignature tool(String name) {
    return tool(name, null, null);
  }

  private static ToolSignature tool(String name, @Nullable Documentation docs) {
    return tool(name, null, docs);
  }

  private static ToolSignature tool(
      String name, @Nullable String checker, @Nullable Documentation docs) {
    return new ToolSignature(
        name, checker != null ? new MobileFunction(checker) : null, docs, true);
  }

  private static Action action(
      String tool, String input, String output) {
    return action(
        tool, Collections.singletonList(input),
        Collections.singletonList(output));
  }

  private static Action action(
      String tool, List<String> inputs, List<String> outputs) {
        return new Action(
        tool, globs(inputs), globs(outputs), ImmutableMap.<String, Object>of());
  }
}
