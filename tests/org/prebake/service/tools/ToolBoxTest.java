package org.prebake.service.tools;

import org.prebake.fs.FileHashes;
import org.prebake.js.Executor;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ToolBoxTest extends PbTestCase {
  private FileSystem fs;
  private Path root;
  private Environment env;
  private FileHashes fh;
  private ScheduledExecutorService execer;
  private File tempDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Logger logger = getLogger(Level.INFO);
    fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs:///#/root/cwd"));
    root = fs.getPath("/root");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    tempDir = makeTempDir();
    env = new Environment(tempDir, envConfig);
    fh = new FileHashes(env, root, logger);
    execer = new ScheduledThreadPoolExecutor(4);
  }

  @Override
  protected void tearDown() throws Exception {
    execer.shutdown();
    fh.close();
    env.close();
    fs.close();
    rmDirTree(tempDir);
    super.tearDown();
  }

  public final void testToolBox() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool', check: function (product) { } })"),
            "/tools/foo.js", "({ help: 'foo1' })",
            "/root/cwd/tools/baz.js", "({})",
            "/root/cwd/tools/foo.js", "({ help: 'foo2' })")
        .assertSigs(
            "{\"name\":\"bar\",\"doc\":\"an example tool\"}",
            "{\"name\":\"foo\",\"doc\":\"foo1\"}",
            "{\"name\":\"baz\",\"doc\":null}");
  }

  public final void testToolFileThrows() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool', check: function (product) { } })"),
            "/tools/foo.js", "({ help: 'foo1' })",
            "/root/cwd/tools/baz.js", "throw 'Bad tool'",  // Bad
            "/root/cwd/tools/foo.js", "({ help: 'foo2' })")
        .assertSigs(
            "{\"name\":\"bar\",\"doc\":\"an example tool\"}",
            "{\"name\":\"foo\",\"doc\":\"foo1\"}"
            // No tool baz
            );
  }

  public final void testBuiltin() throws Exception {
    new TestRunner()
        .withToolDirs("/tools", "/root/cwd/tools")
        .withToolFiles(
            "/tools/bar.js", (
                "({ help: 'an example tool', check: function (product) { } })"),
            "/tools/foo.js", (
                "({ help: 'foo1', fire: function () { return exec('foo') } })"))
        .withBuiltinTools("cp.js")
        .assertSigs(
            ("{"
               + "\"name\":\"cp\","
               + "\"doc\":\"Copies files to a directory tree.  TODO usage\""
             + "}"),
            "{\"name\":\"bar\",\"doc\":\"an example tool\"}",
            "{\"name\":\"foo\",\"doc\":\"foo1\"}");
  }

  public final void testChaining() throws Exception {
    new TestRunner()
        .withToolDirs("/root/cwd/tools", "/tools")
        .withToolFiles(
            "/root/cwd/tools/a.js", (
                "var o = load('next')(this); o.help += 'bar'; o"),
            "/tools/a.js", "({ help: 'foo' })")
        .assertSigs("{\"name\":\"a\",\"doc\":\"foobar\"}");
  }

  public final void testRunawayScripts() throws Exception {
    final Object[] result = new Object[1];
    synchronized (result) {
      Thread th = new Thread(new Runnable() {
        public void run() {
          result[0] = "starting";
          try {
            new TestRunner()
                .withToolDirs("/tools")
                .withToolFiles("/tools/tool.js", "while (1) {}")
                .assertSigs();
          } catch (Exception ex) {
            synchronized (result) {
              result[0] = ex.getMessage();
              result.notify();
            }
          }
          synchronized (result) {
            result[0] = "done";
            result.notify();
          }
        }
      });
      th.setDaemon(true);
      th.setName(getName());
      th.start();
      while (!"done".equals(result[0])) {
        result.wait(20000);
        break;
      }
    }
    assertEquals("done", result[0]);
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
      fh.update(toUpdate);
      return this;
    }

    TestRunner withBuiltinTools(String... fileNames) {
      builtins.addAll(Arrays.asList(fileNames));
      return this;
    }

    void assertSigs(String... expectedSigs) throws Exception {
      ToolBox tb = new ToolBox(fh, toolDirs, getLogger(Level.FINE), execer) {
        @Override
        protected Iterable<String> getBuiltinToolNames() { return builtins; }
      };
      List<ToolSignature> actualSigs = Lists.newArrayList();
      try {
        tb.start();

        List<Future<ToolSignature>> sigs;
        sigs = tb.getAvailableToolSignatures();
        for (Future<ToolSignature> sig : sigs) {
          ToolSignature actualSig = sig.get();
          if (actualSig != null) { actualSigs.add(actualSig); }
        }
      } finally {
        tb.close();
      }

      assertEquals(
          Joiner.on(" ; ").join(expectedSigs),
          Joiner.on(" ; ").join(actualSigs));
    }
  }

  // TODO: test directories that initially don't exist, are created, deleted,
  // recreated.
}
