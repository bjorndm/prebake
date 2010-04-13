package org.prebake.fs;

import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DirectoryHooksTest extends PbTestCase {

  private static final FileAttribute<?>[] FILE_ATTRS = FilePerms.perms(
      0600, false);

  @Test public final void testOnStubFileSystem() throws Exception {
    int delay = 100;
    FileSystem fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
    Path dir = fs.getPath("/foo/bar");
    mkdirs(dir);
    runTests(delay, fs, dir);
  }

  @Test public final void testOnDefaultFileSystem() throws Exception {
    int delay = 100;
    FileSystem fs = FileSystems.getDefault();
    Path dir = fs.getPath("" + Files.createTempDir());
    try {
      runTests(delay, fs, dir);
    } finally {
      rmDirTree(new File(dir.toString()));
    }
  }

  private void runTests(int delay, FileSystem fs, Path dir)
      throws IOException, InterruptedException {
    Path baz = dir.resolve(fs.getPath("baz"));
    dir.resolve(fs.getPath("README")).createFile(FILE_ATTRS);
    mkdirs(baz);
    baz.resolve(fs.getPath("boo.cc")).createFile(FILE_ATTRS);
    baz.resolve(fs.getPath("boo.h")).createFile(FILE_ATTRS);
    DirectoryHooks dh = new DirectoryHooks(
        dir,
        new Predicate<Path>() {
          public boolean apply(Path p) {
            return p.getName().toString().equals("ignored");
          }
        });
    dh.start();
    BlockingQueue<Path> q = dh.getUpdates();

    assertChanged(
        q, delay,
        dir.resolve(fs.getPath("README")),
        baz.resolve(fs.getPath("boo.cc")),
        baz.resolve(fs.getPath("boo.h")));

    // Create a file
    Path boo2cc = baz.resolve(fs.getPath("boo2.cc"));
    boo2cc.createFile(FILE_ATTRS);
    assertChanged(q, delay, boo2cc);

    // Modify a file
    Path boocc = baz.resolve(fs.getPath("boo.cc"));
    OutputStream out = boocc.newOutputStream();
    Writer w = new OutputStreamWriter(out, Charsets.UTF_8);
    w.write("\nprintf(\"Hello, World!\\n\");\n");
    w.close();
    assertChanged(q, delay, boocc);

    // Delete a file
    Path README = dir.resolve(fs.getPath("README"));
    Path LICENSE = dir.resolve(fs.getPath("LICENSE"));
    README.deleteIfExists();
    LICENSE.deleteIfExists();  // does not exist
    assertChanged(q, delay, README);

    // Create a directory and put a file in it.
    Path noo = dir.resolve(fs.getPath("noo"));
    mkdirs(noo);
    Path footxt = noo.resolve(fs.getPath("foo.txt"));
    footxt.createFile(FILE_ATTRS);
    assertChanged(q, delay, footxt);

    // Recreate the deleted file
    README.createFile(FILE_ATTRS);
    assertChanged(q, delay, README);

    // Create an ignored file
    dir.resolve("ignored").createFile(FILE_ATTRS);
    dir.resolve("notIgnored").createFile(FILE_ATTRS);
    assertChanged(q, delay, dir.resolve("notIgnored"));
  }

  private static void assertChanged(
      BlockingQueue<Path> q, int delayMillis, Path... expected)
      throws InterruptedException {
    Set<Path> golden = Sets.newHashSet(expected);
    Set<Path> actual = Sets.newHashSet();
    long t = System.currentTimeMillis();
    long te = t + delayMillis;
    do {
      Path p = q.poll(Math.max(1, te - t), TimeUnit.MILLISECONDS);
      if (p != null) {
        actual.add(p);
        q.drainTo(actual);
      }
      t = System.currentTimeMillis();
    } while (t < te);
    assertEquals(golden, actual);
  }
}
