package org.prebake.service.tools;

import org.prebake.fs.FileHashes;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import junit.framework.TestCase;

public class ToolBoxTest extends TestCase {
  public final void testToolBox() throws Exception {
    Logger logger = Logger.getLogger(getName());
    FileSystem fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs:///#/root/cwd"));
    Path root = fs.getPath("/root");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    File tempFile = File.createTempFile(getName(), ".bdb");
    tempFile.delete();
    tempFile.mkdirs();
    Environment env = new Environment(tempFile, envConfig);
    FileHashes fh = new FileHashes(env, root, logger);
    List<Path> toolDirs = Arrays.asList(
        fs.getPath("/tools"), fs.getPath("/root/cwd/tools"));
    ScheduledExecutorService execer = new ScheduledThreadPoolExecutor(4);
    fs.getPath("/tools").createDirectory();
    writeFile(
        fs.getPath("/tools/bar.js"),
        "({ help: 'an example tool', check: function (product) { } })");
    writeFile(fs.getPath("/tools/foo.js"), "({ help: 'foo1' })");
    fs.getPath("/root").createDirectory();
    fs.getPath("/root/cwd").createDirectory();
    fs.getPath("/root/cwd/tools").createDirectory();
    writeFile(fs.getPath("/root/cwd/tools/baz.js"), "({})");
    writeFile(fs.getPath("/root/cwd/tools/foo.js"), "({ help: 'foo2' })");
    ToolBox tb = new ToolBox(fh, toolDirs, logger, execer);
    List<Future<ToolSignature>> sigs;
    try {
      sigs = tb.getAvailableToolSignatures();
    } finally {
      tb.close();
    }
    List<ToolSignature> actualSigs = Lists.newArrayList();
    for (Future<ToolSignature> sig : sigs) {
      ToolSignature actualSig = sig.get();
      if (actualSig != null) { actualSigs.add(actualSig); }
    }
    assertEquals(
        "TODO",
        Joiner.on(" ; ").join(actualSigs));
  }

  private void writeFile(Path p, String content) throws IOException {
    OutputStream out = p.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      out.write(content.getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }


  // TODO: test directories that initially don't exist, are created, deleted,
  // recreated.
}
