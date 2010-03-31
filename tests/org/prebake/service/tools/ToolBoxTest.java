package org.prebake.service.tools;

import org.prebake.fs.FileHashes;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
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
  private ToolBox tb;
  private File tempFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Logger logger = getLogger(Level.INFO);
    fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs:///#/root/cwd"));
    root = fs.getPath("/root");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    tempFile = File.createTempFile(getName(), ".bdb");
    tempFile.delete();
    tempFile.mkdirs();
    env = new Environment(tempFile, envConfig);
    fh = new FileHashes(env, root, logger);
    execer = new ScheduledThreadPoolExecutor(4);
  }

  @Override
  protected void tearDown() throws Exception {
    if (tb != null) { tb.close(); }
    execer.shutdown();
    fh.close();
    env.close();
    fs.close();
    rmDirTree(tempFile);
    super.tearDown();
  }

  public final void testToolBox() throws Exception {
    mkdirs(fs.getPath("/tools"));
    mkdirs(fs.getPath("/root/cwd/tools"));
    List<Path> toolDirs = Arrays.asList(
        fs.getPath("/tools"), fs.getPath("/root/cwd/tools"));
    writeFile(
        fs.getPath("/tools/bar.js"),
        "({ help: 'an example tool', check: function (product) { } })");
    writeFile(fs.getPath("/tools/foo.js"), "({ help: 'foo1' })");
    writeFile(fs.getPath("/root/cwd/tools/baz.js"), "({})");
    writeFile(fs.getPath("/root/cwd/tools/foo.js"), "({ help: 'foo2' })");
    fh.update(Arrays.asList(
        fs.getPath("/tools/bar.js"), fs.getPath("/tools/foo.js"),
        fs.getPath("/root/cwd/tools/baz.js"),
        fs.getPath("/root/cwd/tools/foo.js")));
    tb = new ToolBox(fh, toolDirs, getLogger(Level.FINE), execer) {
      @Override protected Iterable<String> getBuiltinToolNames() {
        return Collections.<String>emptyList();
      }
    };
    tb.start();

    List<Future<ToolSignature>> sigs;
    sigs = tb.getAvailableToolSignatures();
    List<ToolSignature> actualSigs = Lists.newArrayList();
    for (Future<ToolSignature> sig : sigs) {
      ToolSignature actualSig = sig.get();
      if (actualSig != null) { actualSigs.add(actualSig); }
    }
    assertEquals(
        "{\"name\":\"bar.js\",\"doc\":\"an example tool\"}"
        + " ; {\"name\":\"foo.js\",\"doc\":\"foo1\"}"
        + " ; {\"name\":\"baz.js\",\"doc\":null}",
        Joiner.on(" ; ").join(actualSigs));
  }

  // TODO: test directories that initially don't exist, are created, deleted,
  // recreated.
  // TODO: figure out why updater doesn't stop after 4 tries.
  // TODO: test builtins.
  // TODO: test implementation chaining using load('next')
}
