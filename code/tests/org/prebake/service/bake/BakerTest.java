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

import org.prebake.core.BoundName;
import org.prebake.core.Documentation;
import org.prebake.core.GlobRelation;
import org.prebake.fs.StubFileVersioner;
import org.prebake.js.JsonSink;
import org.prebake.js.MobileFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubOperatingSystem;
import org.prebake.service.HighLevelLog;
import org.prebake.service.Logs;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
            "  logs/",
            "    foo.product.log",  // We haven't installed the hydra so no log.
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
       .withProduct(product("p", action("cp", "i/*", "o/*")))
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
           "  logs/",
           "    p.product.log",
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
           "  logs/",
           "    p.product.log",
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
       .withProduct(product("p", action("cp", "i/*", "o/*")))
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
           "  logs/",
           "    p.product.log",
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
           "  logs/",
           "    p.product.log",
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
       .withProduct(product("p", action("cp", "i/*", "o/*")))
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
           "  logs/",
           "    p.product.log",
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
           "        a \"a\"",      // o/b is gone
           "      .prebake/",
           "        archive/",
           "          o/",
           "            b \"b\"",  // The output file is archived.
           "  logs/",
           "    p.product.log",
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
           BoundName.fromString("p"),
           null, new GlobRelation(globs("i/**"), globs("o/**", "p/j/g")),
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
           "  logs/",
           "    p.product.log",
           "  tmpdir/")
       .assertProductStatus("p", true);
  }

  @Test public final void testProcessReturnsErrorCode() throws Exception {
    String failJs = (
        "({fire: function (inputs, product, action, os) { return os.failed; }})"
        );
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      fail.js \"" + failJs + "\"",
        "    root/",
        "      a \"a\"")
        .withTool(tool("fail"), "/cwd/tools/fail.js")
        .withProduct(product("p", action("fail", "a", "b")))
        .expectSuccess(false)
        .build("p")
        .runPendingTasks()
        .assertProductStatus("p", false)
        .assertLog(
            "INFO: Starting bake of product p",
            "WARNING: Failed to build product p : false");
  }

  @Test public final void testProcessNotWaitedFor() throws Exception {
    String badJs = (
        ""
        + "({fire: function (inputs, product, action, os) {\n"
        + "  os.exec('cp', 'a', 'b').run();\n"  // Run but not waited for.
        + "  return os.passed;\n"
        + "}})"
        );
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      bad.js " + JsonSink.stringify(badJs) + "",
        "    root/",
        "      a \"foo\"",
        "  tmpdir/")
        .withTool(tool("bad"), "/cwd/tools/bad.js")
        .withProduct(product("p", action("bad", "a", "b")))
        .expectSuccess(false)
        .build("p")
        .runPendingTasks()
        .assertProductStatus("p", false)
        .assertLog("WARNING: Aborted still running process cp")
        .assertFileTree(
            "/",
            "  cwd/",
            "    tools/",
            "      bad.js \"...\"",
            "    root/",
            "      a \"foo\"",
            "  tmpdir/",
            "  logs/",
            "    p.product.log");
  }

  @Test public final void testDerivedProductGarbageCollected()
      throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      cp.js " + COPY_TOOL_JS,
        "    root/",
        "      foo/",
        "        bar \"bar\"",
        "  tmpdir/")
        .withTool(tool("cp"), "/cwd/tools/cp.js")
        .withProduct(product("p", action("cp", "*(x)/bar", "*(x).out/bar"))
            .withParameterValues(ImmutableMap.of("x", "foo")))
        .expectSuccess(true)
        .build("p[\"x\":\"foo\"]")
        .runPendingTasks()
        .assertProductStatus("p[\"x\":\"foo\"]", true)
        .assertFileTree(
            "/",
            "  cwd/",
            "    tools/",
            "      cp.js \"...\"",
            "    root/",
            "      foo/",
            "        bar \"bar\"",
            "      foo.out/",
            "        bar \"bar\"",
            "  tmpdir/",
            "  logs/",
            "    p+5b.22+x+22.3a.22+foo+22.5d+.product.log")
        .writeFile("root/foo/bar", "baz")
        .assertNoSuchProduct("p[\"x\":\"foo\"]");
  }

  public static final String LS_TOOL_JS = JsonSink.stringify(
      ""
      + "({ \n"
      + "  fire: function fire(inputs, product, action, os) { \n"
      + "    return os.exec(['ls'].concat(inputs).concat(action.outputs[0]));\n"
      + "  } \n"
      + "})");

  @Test public void testInputOrder() throws Exception {
    tester.withFileSystem(
        "/",
        "  cwd/",
        "    tools/",
        "      ls.js " + LS_TOOL_JS,
        "    root/",
        "      a+",
        "      b-",
        "      c+",
        "      d-",
        "  tmpdir/")
        .withTool(tool("ls"), "/cwd/tools/ls.js")
        .withProduct(product(
            "p",
            action(
                "ls",
                ImmutableList.of("*-", "*+"),
                ImmutableList.of("list"))))
        .expectSuccess(true)
        .build("p")
        .runPendingTasks()
        .assertProductStatus("p", true)
        .assertFileTree(
            "/",
            "  cwd/",
            "    tools/",
            "      ls.js \"...\"",
            "    root/",
            "      a+",
            "      b-",
            "      c+",
            "      d-",
            // Since the order of input globs above is *-, *+, we get that order
            // below.
            "      list \"b-\\nd-\\na+\\nc+\\n\"",
            "  tmpdir/",
            "  logs/",
            "    p.product.log");
  }

  // TODO: a derived product is invalidated when a file it would match is added.
  // And this doesn't invalidate other products derived from the same template.
  // TODO: a derived product is invalidated when its template is changed.
  // TODO: output globs that overlap inputs
  // TODO: changed output is updated
  // TODO: changing an input invalidates a product and any products
  // that depend on it.
  // TODO: actions time out
  // TODO: process that takes a long time.
  // TODO: wrapping a process by using the old process as a prototype returns
  // the wrapper from run() and other methods.
  // TODO: moving an output to the client would require moving a directory into
  // the archive clobbering.
  // TODO: moving an output to the client would require creating a directory
  // over a file that should be archived.

  private final class Tester {
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
      TestClock clock = new TestClock();
      logger = getLogger(Level.INFO);
      logHydra = new TestLogHydra(logger, fs.getPath("/logs"), clock);
      fs.getPath("/logs").createDirectory();
      Logs logs = new Logs(new HighLevelLog(clock), logger, logHydra);
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
      baker = new Baker(os, files, getCommonJsEnv(), 0700, logs, execer);
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

    Tester build(String productName, String... prereqs)
        throws ExecutionException, InterruptedException {
      int n = prereqs.length;
      BoundName[] prereqArr = new BoundName[n];
      for (int i = 0; i < n; ++i) {
        prereqArr[i] = BoundName.fromString(prereqs[i]);
      }
      return build(BoundName.fromString(productName), prereqArr);
    }

    Tester build(BoundName productName, BoundName... prereqs)
        throws ExecutionException, InterruptedException {
      Boolean result = baker.bake(
          productName, ImmutableList.copyOf(prereqs)).get();
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

    Tester assertNoSuchProduct(String productName) {
      try {
        baker.unittestBackdoorProductStatus(productName);
        fail("Product exists " + productName);
      } catch (NoSuchElementException ex) {
        // OK
      }
      return this;
    }

    /**
     * Asserts that the log contains at least the entries given.
     * @param logEntries in the order they appear in the log.
     */
    Tester assertLog(String... logEntries) {
      int i = 0, n = logEntries.length;
      Iterator<String> it = getLog().iterator();
      while (i < n) {
        if (!it.hasNext()) {
          fail("Not in log : " + Arrays.asList(logEntries).subList(i, n));
        }
        String actualLogEntry = it.next();
        if (actualLogEntry.equals(logEntries[i])) { ++i; }
      }
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
        BoundName.fromString(name), null,
        new GlobRelation(action.inputs, action.outputs),
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
    return new Action(tool, globs(input), globs(output), options);
  }

  private static Action action(
      String tool, List<String> inputs, List<String> outputs) {
    return new Action(
        tool, globs(inputs), globs(outputs), ImmutableMap.<String, Object>of());
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
