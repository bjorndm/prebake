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

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.fs.StubFileVersioner;
import org.prebake.js.JsonSink;
import org.prebake.js.MobileFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubOperatingSystem;
import org.prebake.service.TestLogHydra;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolContent;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubScheduledExecutorService;
import org.prebake.util.TestClock;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

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

@ParametersAreNonnullByDefault
public class BakerTest extends PbTestCase {
  private Tester tester;

  @Before
  public void init() {
    tester = new Tester();
  }

  @After
  public void tearDown() throws IOException {
    tester.close();
  }

  @Test
  public final void testCopyFooDirectoryToBaz() throws Exception {
    final String fooBuiltLogMessage = "INFO: Starting bake of product foo";
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    root/",
        "      foo/",
        "        a.txt  \"foo a text\"",
        "        a.html \"foo a html\"",
        "        b.txt  \"foo b text\"",
        "        b.html \"foo b html\"",
        "      bar/",
        "        a.txt  \"bar a text\"",
        "        a.html \"bar a html\"",
        "        b.txt  \"bar b text\"",
        "        b.html \"bar b html\"",
        "      tools/",
        ("        cp.js  \"({ \\n"
         + "  fire: function fire(inputs, product, action, os) { \\n"
         + "  var opts = action.options; \\n"
         // Sanity check all the inputs.
         + "    if (typeof opts !== 'object' \\n"
         // See options map below.
         + "        || (JSON.stringify({foo:'bar'}) \\n"
         + "            !== JSON.stringify(opts))) { \\n"
         + "      throw new Error('wrong options'); \\n"
         + "    } \\n"
         + "    if (!(inputs instanceof Array)) { \\n"
         + "      throw new Error('' + inputs); \\n"
         + "    } \\n"
         + "    if (action.tool !== 'cp') { \\n"
         + "      throw new Error('action ' + JSON.stringify(action)); \\n"
         + "    } \\n"
         + "    if ('foo' !== product.name) { \\n"
         + "      throw new Error('product ' + JSON.stringify(product)); \\n"
         + "    } \\n"
         + "    if (typeof os !== 'object') { \\n"
         + "      throw new Error('os ' + os); \\n"
         + "    } \\n"
         + "    if (typeof os.exec !== 'function') { \\n"
         + "      throw new Error('os.exec ' + os.exec); \\n"
         + "    } \\n"
         // Infer outputs from inputs
         + "    var xform = glob.xformer(action.inputs, action.outputs); \\n"
         + "    var processes = []; \\n"
         + "    for (var i = 0, n = inputs.length; i < n; ++i) { \\n"
         + "      var input = inputs[i]; \\n"
         + "      var output = xform(input); \\n"
         + "      console.log('  input=' + input + ', output=' + output); \\n"
         + "      processes.push(os.exec('cp', input, output)); \\n"
         + "    } \\n"
         + "    return { \\n"
         + "      run: function () { \\n"
         + "        for (var i = 0, n = processes.length; i < n; ++i) { \\n"
         + "          processes[i].run(); \\n"
         + "        } \\n"
         + "        return this; \\n"
         + "      }, \\n"
         + "      waitFor: function () { \\n"
         + "        for (var i = 0, n = processes.length; i < n; ++i) { \\n"
         + "          var result = processes[i].waitFor(); \\n"
         + "          if (result !== 0) { return result; } \\n"
         + "        } \\n"
         + "        return 0; \\n"
         + "      } \\n"
         + "    }; \\n"
         + "  } \\n"
         + "})\""))
        .withProduct(product(
            "foo",
            action("cp", ImmutableMap.of("foo", "bar"), "foo/**", "baz/**")))
        .withTool(tool("cp"), "/cwd/root/tools/cp.js")
        .expectSuccess(true)
        .build("foo")
        .runPendingTasks()  // To delete temporary files
        .assertFileTree(
            "/",
            "  cwd/",
            "    root/",
            "      foo/",
            "        a.txt \"foo a text\"",
            "        a.html \"foo a html\"",
            "        b.txt \"foo b text\"",
            "        b.html \"foo b html\"",
            "      bar/",
            "        a.txt \"bar a text\"",
            "        a.html \"bar a html\"",
            "        b.txt \"bar b text\"",
            "        b.html \"bar b html\"",
            "      tools/",
            "        cp.js \"...\"",
            "      baz/",
            "        a.html \"foo a html\"",
            "        a.txt \"foo a text\"",
            "        b.html \"foo b html\"",
            "        b.txt \"foo b text\"",
            "  tmpdir/")
        .assertLog(fooBuiltLogMessage)
        .assertProductStatus("foo", true)
        .clearLog()
        .build("foo")  // Second build should do nothing.
        .assertProductStatus("foo", true);
    // Now, check that foo was not rebuilt unnecessarily
    assertFalse(
        Joiner.on('\n').join(getLog()),
        getLog().contains(fooBuiltLogMessage));
  }

  @Test()
  public final void testUnrecognizedProduct() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    root/",
        "      foo/",
        "        a.txt  \"foo a text\"",
        "      bar/",
        "        b.txt  \"bar b text\"",
        "      tools/",
        ("        cp.js  \"({fire: function fire() { throw new Error; }}\""))
        .withProduct(product(
            "foo", action("cp", ImmutableMap.of("x", "y"), "foo/**", "baz/**")))
        .withTool(tool("cp"), "/cwd/root/tools/cp.js")
        .expectSuccess(false)
        .build("bar")
        .assertLog("WARNING: Unrecognized product bar");
  }

  private static final String BORK_TOOL_JS = JsonSink.stringify(
      ""
      + "({\n"
      + "  fire: function (inputs, product, action, os) {\n"
      + "    var opts = action.options; \n"
      + "    var argv = action.outputs.slice(0);\n"
      + "    argv.splice(0, 0, 'bork');\n"
      + "    return os.exec.apply({}, argv).run();\n"
      + "  }\n"
      + "})");

  private static final String SORTED_BORK_TOOL_JS = JsonSink.stringify(
      ""
      + "({\n"
      + "  fire: function (inputs, product, action, os) {\n"
      + "    var opts = action.options; \n"
      + "    var argv = action.outputs.slice(0);\n"
      + "    argv.splice(0, 0, 'bork');\n"
      + "    argv.sort();\n"
      + "    return os.exec.apply({}, argv).run();\n"
      + "  }\n"
      + "})");

  @Test
  public final void testProductStatusDependsOnTools() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    root/",
        "      tools/",
        "        bork.js " + BORK_TOOL_JS,
        "      tools2/",
        "        bork.js " + SORTED_BORK_TOOL_JS)
       .withTool(tool("bork"), "root/tools/bork.js")
       .withProduct(product(
           "swedish_meatballs",
           action(
               "bork", ImmutableList.<String>of(),
               ImmutableList.of("bork!", "bork/bork!", "bork/bork/bork!"))))
       .expectSuccess(true)
       .build("swedish_meatballs")
       .assertProductStatus("swedish_meatballs", true)
       .withTool(tool("bork"), "root/tools2/bork.js")
       .assertProductStatus("swedish_meatballs", false);
  }

  @Test
  public final void testProductStatusDependsOnProductDef() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    root/",
        "      tools/",
        "        bork.js " + BORK_TOOL_JS)
       .withTool(tool("bork"), "root/tools/bork.js")
       .withProduct(product(
           "swedish_meatballs",
           action(
               "bork", ImmutableList.<String>of(),
               ImmutableList.of("bork!", "bork/bork!", "bork/bork/bork!"))))
       .expectSuccess(true)
       .build("swedish_meatballs")
       .assertProductStatus("swedish_meatballs", true)
       .withProduct(product(
           "swedish_meatballs",
           action(
               "bork", ImmutableList.<String>of(),
               ImmutableList.of("unbork!"))))
       .assertProductStatus("swedish_meatballs", false);
  }

  @Test
  public final void testProductStatusDependsOnOtherProductDefs()
      throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    root/",
        "      tools/",
        "        bork.js " + BORK_TOOL_JS)
       .withTool(tool("bork"), "root/tools/bork.js")
       .withProduct(product(
           "swedish_meatballs",
           action(
               "bork", ImmutableList.<String>of(),
               ImmutableList.of("bork!", "bork/bork!", "bork/bork/bork!"))))
       .expectSuccess(true)
       .build("swedish_meatballs")
       .assertProductStatus("swedish_meatballs", true)
       .withProduct(product(
           "french_fries",
           action(
               "fries", ImmutableList.<String>of(),
               ImmutableList.of("fries!"))))
       .assertProductStatus("swedish_meatballs", true);
  }

  public static final String COPY_TOOL_JS = JsonSink.stringify(
      ""
      + "({ \n"
      + "  fire: function fire(inputs, product, action, os) { \n"
      + "    var opts = action.options; \n"
      // Infer outputs from inputs
      + "    var outGlob = action.outputs[0]; \n"
      + "    var inGlob = action.inputs[0]; \n"
      + "    var xform = glob.xformer(action.inputs, action.outputs); \n"
      + "    var processes = []; \n"
      + "    for (var i = 0, n = inputs.length; i < n; ++i) { \n"
      + "      var input = inputs[i]; \n"
      + "      processes.push(os.exec('cp', input, xform(input)).run()); \n"
      + "    } \n"
      + "    return { \n"
      + "      run: function () { \n"
      + "        for (var i = 0, n = processes.length; i < n; ++i) { \n"
      + "          processes[i].run(); \n"
      + "        } \n"
      + "        return this; \n"
      + "      }, \n"
      + "      waitFor: function () { \n"
      + "        for (var i = 0, n = processes.length; i < n; ++i) { \n"
      + "          var result = processes[i].waitFor(); \n"
      + "          if (result !== 0) { return result; } \n"
      + "        } \n"
      + "        return 0; \n"
      + "      } \n"
      + "    }; \n"
      + "  } \n"
      + "})");

  public static final String MUNGE_TOOL_JS = JsonSink.stringify(
      ""
      + "({ \n"
      + "  fire: function fire(inputs, product, action, os) { \n"
      + "    var opts = action.options; \n"
      // Infer outputs from inputs
      + "    var outGlob = action.outputs[0]; \n"
      + "    var inGlob = action.inputs[0]; \n"
      + "    var xform = glob.xformer(action.inputs, action.outputs); \n"
      + "    var processes = []; \n"
      + "    for (var i = 0, n = inputs.length; i < n; ++i) { \n"
      + "      var input = inputs[i]; \n"
      + "      var output = xform(input); \n"
      + "      os.mkdirs(os.dirname(output)); \n"
      + "      processes.push(os.exec('munge', input, output).run()); \n"
      + "    } \n"
      + "    return { \n"
      + "      run: function () { \n"
      + "        for (var i = 0, n = processes.length; i < n; ++i) { \n"
      + "          processes[i].run(); \n"
      + "        } \n"
      + "        return this; \n"
      + "      }, \n"
      + "      waitFor: function () { \n"
      + "        for (var i = 0, n = processes.length; i < n; ++i) { \n"
      + "          var result = processes[i].waitFor(); \n"
      + "          if (result !== 0) {\n"
      + "            throw new Error('Failed to munge ' + inputs[i]); \n"
      + "          } \n"
      + "        } \n"
      + "        return 0; \n"
      + "      } \n"
      + "    }; \n"
      + "  } \n"
      + "})");

  @Test
  public final void testProductChangesWhenInputChanged() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      cp.js " + COPY_TOOL_JS,
        "    root/",
        "      i/",
        "        a \"a\"",
        "      o/")
       .withTool(tool("cp"), "/cwd/tools/cp.js")
       .withProduct(product(
           "p", action("cp", ImmutableList.of("i/*"), ImmutableList.of("o/*"))))
       .expectSuccess(true)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"a\"",
           "      o/",
           "        a \"a\"",
           "  tmpdir/")
       .assertProductStatus("p", true)
       .writeFile("/cwd/root/i/a", "A")
       .assertProductStatus("p", false)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"A\"",
           "      o/",
           "        a \"A\"",
           "  tmpdir/");
  }

  @Test
  public final void testProductChangesWhenInputCreated() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      cp.js " + COPY_TOOL_JS,
        "    root/",
        "      i/",
        "        a \"a\"",
        "      o/")
       .withTool(tool("cp"), "/cwd/tools/cp.js")
       .withProduct(product(
           "p", action("cp", ImmutableList.of("i/*"), ImmutableList.of("o/*"))))
       .expectSuccess(true)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"a\"",
           "      o/",
           "        a \"a\"",
           "  tmpdir/")
       .assertProductStatus("p", true)
       .writeFile("/cwd/root/i/b", "b")
       .assertProductStatus("p", false)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"a\"",
           "        b \"b\"",
           "      o/",
           "        a \"a\"",
           "        b \"b\"",
           "  tmpdir/");
  }

  @Test
  public final void testProductChangesWhenInputDeleted() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      cp.js " + COPY_TOOL_JS,
        "    root/",
        "      i/",
        "        a \"a\"",
        "        b \"b\"",
        "      o/")
       .withTool(tool("cp"), "/cwd/tools/cp.js")
       .withProduct(product(
           "p", action("cp", ImmutableList.of("i/*"), ImmutableList.of("o/*"))))
       .expectSuccess(true)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"a\"",
           "        b \"b\"",
           "      o/",
           "        a \"a\"",
           "        b \"b\"",
           "  tmpdir/")
       .assertProductStatus("p", true)
       .deleteFile("/cwd/root/i/b")
       .assertProductStatus("p", false)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "    root/",
           "      i/",
           "        a \"a\"",
           "      o/",
           "        a \"a\"",
           "      .prebake/",
           "        archive/",
           "          o/",
           "            b \"b\"",
           "  tmpdir/")
      .assertLog(
          "INFO: 1 obsolete file(s) can be found under "
          + "/cwd/root/.prebake/archive");
  }


  @Test public final void testProductWithBakeMethod() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      cp.js " + COPY_TOOL_JS,
        "      munge.js " + MUNGE_TOOL_JS,
        "    root/",
        "      i/",
        "        j/",
        "          c \"The cat in the hat\"",
        "          g \"Green eggs and ham\"",
        "        h \"Horton hears a who\"")
       .withTool(tool("cp"), "/cwd/tools/cp.js")
       .withTool(tool("munge"), "/cwd/tools/munge.js")
       .withProduct(new Product(
           "p", null, ImmutableList.of(Glob.fromString("i/**")),
           ImmutableList.of(Glob.fromString("o/**"), Glob.fromString("p/j/g")),
           ImmutableList.of(
               action("cp", "o/j/*", "p/j/*"),  // Run later
               action("munge", "i/**", "o/**")),  // Run
           false,
           new MobileFunction(Joiner.on('\n').join(  // Runs them out of order.
               "function (actions) {",
               "  return actions[1]().run().waitFor() == 0",
               "      && actions[0]().run().waitFor() == 0;",
               "}")),
           tester.fs.getPath("plans/p.js")))
       .expectSuccess(true)
       .build("p")
       .runPendingTasks()
       .assertProductStatus("p", true)
       .assertFileTree(
           "/",
           "  cwd/",
           "    tools/",
           "      cp.js \"...\"",
           "      munge.js \"...\"",
           "    root/",
           "      i/",
           "        j/",
           "          c \"The cat in the hat\"",
           "          g \"Green eggs and ham\"",
           "        h \"Horton hears a who\"",
           "      o/",
           "        j/",  // Munge ran first and reversed the content.
           "          c \"tah eht ni tac ehT\"",
           "          g \"mah dna sgge neerG\"",
           "        h \"ohw a sraeh notroH\"",
           "      p/",  // Rest of p excluded from Product output
           "        j/",
           "          g \"mah dna sgge neerG\"",
           "  tmpdir/")
       .assertProductStatus("p", true);
  }

  // TODO: test that unclosed processes result in log messages
  // TODO: output globs that overlap inputs
  // TODO: process returns error code.
  // TODO: changed output is updated
  // TODO: source file deleted and generated file archived
  // TODO: actions time out
  // TODO: process takes a long time.

  private class Tester {
    FileSystem fs;
    private OperatingSystem os;
    private StubFileVersioner files;
    private Logger logger;
    private TestLogHydra logHydra;
    private StubScheduledExecutorService execer;
    private StubToolProvider toolbox;
    private Baker baker;
    private boolean successExpectation;

    Tester withFileSystem(String... asciiArt) throws IOException {
      return withFileSystem(fileSystemFromAsciiArt(
          "/cwd", Joiner.on('\n').join(asciiArt)));
    }

    Tester withFileSystem(FileSystem fs) throws IOException {
      this.fs = fs;
      logger = getLogger(Level.INFO);
      logHydra = new TestLogHydra(logger, fs.getPath("/logs"), new TestClock());
      os = new StubOperatingSystem(fs, logger);
      files = new StubFileVersioner(
          fs.getPath("root").toAbsolutePath(),
          Predicates.<Path>alwaysTrue(), logger);
      final ImmutableList.Builder<Path> b = ImmutableList.builder();
      Files.walkFileTree(fs.getPath("/"), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path f, BasicFileAttributes atts) {
          b.add(f);
          return FileVisitResult.CONTINUE;
        }
      });
      files.updateFiles(b.build());
      execer = new StubScheduledExecutorService();
      toolbox = new StubToolProvider();
      baker = new Baker(
          os, files, getCommonJsEnv(), 0700, logger, logHydra, execer);
      baker.setToolBox(toolbox);
      return this;
    }

    Tester withTool(ToolSignature sig, String path) {
      toolbox.sigs.put(sig.name, sig);
      toolbox.toolPaths.put(sig.name, fs.getPath(path));
      baker.toolListener.artifactChanged(sig);
      return this;
    }

    Tester withProduct(Product p) {
      baker.prodListener.artifactChanged(p);
      return this;
    }

    Tester expectSuccess(boolean success) {
      this.successExpectation = success;
      return this;
    }

    Tester build(String productName)
        throws ExecutionException, InterruptedException {
      Boolean result = baker.bake(productName).get();
      assertEquals(successExpectation, result);
      return this;
    }

    Tester runPendingTasks() {
      execer.advanceTime(1000, logger);
      return this;
    }

    Tester assertFileTree(String... golden) {
      assertEquals(
          Joiner.on('\n').join(golden),
          fileSystemToAsciiArt(files.getFileSystem(), 40).trim());
      return this;
    }

    Tester assertProductStatus(String productName, boolean upToDate) {
      assertEquals(
          productName + " status", upToDate,
          baker.unittestBackdoorProductStatus(productName));
      return this;
    }

    Tester assertLog(String logEntry) {
      assertTrue(logEntry, getLog().contains(logEntry));
      return this;
    }

    Tester clearLog() {
      getLog().clear();
      return this;
    }

    Tester writeFile(String path, String content) throws IOException {
      Path p = fs.getPath(path);
      OutputStream out = p.newOutputStream(
          StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
      Writer w = new OutputStreamWriter(out, Charsets.UTF_8);
      try {
        w.write(content);
      } finally {
        w.close();
      }
      files.updateFiles(Collections.singleton(p));
      return this;
    }

    Tester deleteFile(String path) throws IOException {
      Path p = fs.getPath(path);
      p.delete();
      files.updateFiles(Collections.singleton(p));
      return this;
    }

    void close() throws IOException {
      if (toolbox != null) { toolbox.close(); }
      if (files != null) { files.close(); }
      if (execer != null) { execer.shutdown(); }
      if (fs != null) { fs.close(); }
    }
  }

  private Product product(String name, Action action) {
    return new Product(
        name, null, action.inputs, action.outputs,
        Collections.singletonList(action), false, null,
        tester.fs.getPath("plans/" + name + ".js"));
  }

  private static ToolSignature tool(String name) {
    return tool(name, null, null);
  }

  private static ToolSignature tool(
      String name, @Nullable String checker, @Nullable Documentation docs) {
    return new ToolSignature(
        name, checker != null ? new MobileFunction(checker) : null, docs, true);
  }

  private static Action action(
      String tool, String input, String output) {
    return action(tool, ImmutableMap.<String, Object>of(), input, output);
  }

  private static Action action(
      String tool, ImmutableMap<String, ?> options,
      String input, String output) {
    return new Action(
        tool, Collections.singletonList(Glob.fromString(input)),
        Collections.singletonList(Glob.fromString(output)),
        options);
  }

  private static Action action(
      String tool, List<String> inputs, List<String> outputs) {
    List<Glob> inputGlobs = Lists.newArrayList();
    for (String input : inputs) { inputGlobs.add(Glob.fromString(input)); }
    List<Glob> outputGlobs = Lists.newArrayList();
    for (String output : outputs) { outputGlobs.add(Glob.fromString(output)); }
    return new Action(
        tool, inputGlobs, outputGlobs, ImmutableMap.<String, Object>of());
  }

  final class StubToolProvider implements ToolProvider {
    Map<String, ToolSignature> sigs = Maps.newHashMap();
    Map<String, Path> toolPaths = Maps.newHashMap();
    public List<Future<ToolSignature>> getAvailableToolSignatures() {
      List<Future<ToolSignature>> out = Lists.newArrayList();
      for (ToolSignature sig : sigs.values()) {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(sig);
        out.add(f);
      }
      return out;
    }
    public ToolContent getTool(String toolName) throws IOException {
      Path p = toolPaths.get(toolName);
      if (p == null) { throw new IOException("No tool with name " + toolName); }
      return new ToolContent(tester.files.load(p), false);
    }
    public void close() { /* no-op */ }
  }
}
