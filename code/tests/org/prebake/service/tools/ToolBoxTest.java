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

import org.prebake.core.ArtifactListener;
import org.prebake.fs.DbFileVersioner;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.service.HighLevelLog;
import org.prebake.service.Logs;
import org.prebake.service.TestLogHydra;
import org.prebake.util.PbTestCase;
import org.prebake.util.TestClock;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ToolBoxTest extends PbTestCase {
  private @Nullable FileSystem fs;
  private @Nullable Logs logs;
  private @Nullable Path root;
  private @Nullable Environment env;
  private @Nullable FileVersioner files;
  private @Nullable ScheduledExecutorService execer;
  private @Nullable File tempDir;

  @Before
  public void setUp() throws IOException {
    Logger logger = getLogger(Level.INFO);
    fs = fileSystemFromAsciiArt(
        "/root/cwd",
        "/",
        "  root/",
        "    cwd/",
        "  logs/");
    TestClock clock = new TestClock();
    logs = new Logs(
        new HighLevelLog(clock),
        getLogger(Level.INFO),
        new TestLogHydra(logger, fs.getPath("/logs"), clock));
    root = fs.getPath("/root");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    tempDir = Files.createTempDir();
    env = new Environment(tempDir, envConfig);
    files = new DbFileVersioner(
        env, root, Predicates.<Path>alwaysTrue(), logger);
    execer = new ScheduledThreadPoolExecutor(4);
  }

  @After
  public void closeAll() throws IOException {
    execer.shutdown();
    files.close();
    env.close();
    fs.close();
    rmDirTree(tempDir);
  }

  @Test public final void testToolBox() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool', check: function (product) { }})"
                ),
            "/tools/foo.js", "({ help: 'foo1' })",
            "/root/cwd/tools/baz.js", "({})",
            "/root/cwd/tools/foo.js", "({ help: 'foo2' })")
        .assertSigs(
            "{\"name\":\"bar\",\"help\":\"an example tool\","
            + "\"check\":function _tool_bar$check(product) {\n}}",
            "{\"name\":\"foo\",\"help\":\"foo1\"}",
            "{\"name\":\"baz\"}");
  }

  @Test public final void testToolFileThrows() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool'})"),
            "/tools/foo.js", "({ help: 'foo1' })",
            "/root/cwd/tools/baz.js", "throw 'Bad tool'",  // Bad
            "/root/cwd/tools/foo.js", "({ help: 'foo2' })")
        .assertSigs(
            "{\"name\":\"bar\",\"help\":\"an example tool\"}",
            "{\"name\":\"foo\",\"help\":\"foo1\"}"
            // No tool baz
            );
  }

  @Test public final void testBuiltin() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool',"
                + " check: function (product) { console.log('OK'); } })"),
            "/tools/foo.js", (
                "({ help: 'foo1',"
                + " fire: function (inputs, product, os) {"
                  + " return os.exec('foo')"
                + " } })"))
        .withBuiltinTools("cp.js")
        .assertSigs(
            ("{"
               + "\"name\":\"cp\","
               + "\"help\":{"
                 + "\"summary\":\"Copies files to a directory tree.\","
                 + "\"detail\":\"This version of the cp command copies by glob"
                   + " transform.\\nE.g. to copy all html files under the doc/"
                   + " directory to the same location under the www"
                   + " directory do\\n"
                   + "<code class=\\\"prettyprint lang-js\\\">"
                   + "  tools.cp(&#34;doc/**.html&#34;, &#34;www/**.html&#34;);"
                   + "</code>\","
                 + "\"contact\":\"Mike Samuel <mikesamuel@gmail.com>\""
               + "},"
               + "\"check\":function prelim(action, opt_config) {<elided>}"
             + "}"),
            ("{"
             + "\"name\":\"bar\","
               + "\"help\":\"an example tool\","
               + "\"check\":function _tool_bar$check(product) {\n"
                 + "  console.log(\"OK\");\n}"
             + "}"),
            "{\"name\":\"foo\",\"help\":\"foo1\"}");
  }

  @Test public final void testChaining() throws Exception {
    new TestRunner()
        .withToolDirs("/root/cwd/tools", "/tools")
        .withToolFiles(
            "/root/cwd/tools/a.js", (
                "var o = load('...')(this); o.help += 'bar'; o"),
            "/tools/a.js", "({ help: 'foo' })")
        .assertSigs("{\"name\":\"a\",\"help\":\"foobar\"}");
  }

  @Test(timeout=10000)
  public final void testRunawayScripts() throws Exception {
    new TestRunner()
        .withToolDirs("/tools")
        .withToolFiles("/tools/tool.js", "while (1) {}")
        .assertSigs();
    boolean foundTimeout = false;
    // The ToolBox executes the scripts in other threads so the timeout
    // exceptions should be on the stack.
    String timeoutName = Executor.ScriptTimeoutException.class.getName();
    for (String logMsg : getLog()) {
      if (logMsg.contains(timeoutName)) {
        foundTimeout = true;
        break;
      }
    }
    assertTrue(foundTimeout);
  }

  private final class TestRunner {
    List<Path> toolDirs = Lists.newArrayList();
    List<String> builtins = Lists.newArrayList();

    TestRunner withToolDirs(String... dirs) throws IOException {
      for (String dir : dirs) {
        Path p = fs.getPath(dir);
        toolDirs.add(p);
        mkdirs(p);
      }
      return this;
    }

    TestRunner withToolFiles(String... namesAndContent) throws IOException {
      List<Path> toUpdate = Lists.newArrayList();
      for (int i = 0, n = namesAndContent.length; i < n; i += 2) {
        Path p = fs.getPath(namesAndContent[i]);
        writeFile(p, namesAndContent[i + 1]);
        toUpdate.add(p);
      }
      files.updateFiles(toUpdate);
      return this;
    }

    TestRunner withBuiltinTools(String... fileNames) {
      builtins.addAll(Arrays.asList(fileNames));
      return this;
    }

    void assertSigs(String... expectedSigs) throws Exception {
      ToolBox tb = new ToolBox(
          files, getCommonJsEnv(), toolDirs, logs,
          ArtifactListener.Factory.<ToolSignature>noop(), execer) {
        @Override
        protected Iterable<String> getBuiltinToolNames() { return builtins; }
      };
      List<String> actualSigs = Lists.newArrayList();
      try {
        tb.start();

        List<Future<ToolSignature>> sigs;
        sigs = tb.getAvailableToolSignatures();
        for (Future<ToolSignature> sig : sigs) {
          ToolSignature actualSig = sig.get();
          if (actualSig != null) {
            actualSigs.add(
                JsonSink.stringify(actualSig)
                .replaceAll(
                    ""
                    + "(\\bfunction( \\w+)?\\(action[^\\)]*\\))"
                    + "\\s*\\{([^{}]+|(\\{[^}]*\\}))*\\}",
                    "$1 {<elided>}"));
          }
        }
      } finally {
        tb.close();
      }

      assertEquals(
          Joiner.on(" ; ").join(expectedSigs),
          Joiner.on(" ; ").join(actualSigs));
    }
  }

  // TODO: HIGH: test directories that initially don't exist, are created,
  // deleted, recreated.
  // TODO: HIGH: make sure the fire function can refer to global variables and
  // sigs still work.
  // TODO: HIGH: test tool listeners fired
}
