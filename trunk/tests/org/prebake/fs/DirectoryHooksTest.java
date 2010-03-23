package org.prebake.fs;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.prebake.service.StubFileSystemProvider;

import com.google.common.collect.Sets;

import junit.framework.TestCase;

public class DirectoryHooksTest extends TestCase {

  private static final FileAttribute<?>[] DIR_ATTRS = new FileAttribute<?>[] {
    PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rwx------"))
  };

  private static final FileAttribute<?>[] FILE_ATTRS = new FileAttribute<?>[] {
    PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rw-------"))
  };

  public final void testOnStubFileSystem() throws Exception {
    int delay = 100;
    FileSystem fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
    fs.getPath("/foo").createDirectory();
    Path dir = fs.getPath("/foo/bar");
    dir.createDirectory();
    runTests(delay, fs, dir);
  }

  public final void testOnDefaultFileSystem() throws Exception {
    int delay = 100;
    FileSystem fs = FileSystems.getDefault();
    Path dir = fs.getPath("" + File.createTempFile("temp", "dir"));
    try {
      dir.delete();
      if (dir.notExists()) {
        dir.createDirectory(DIR_ATTRS);
      }
      runTests(delay, fs, fs.getPath(dir.toString()));
    } finally {
      rmTree(dir);
    }
  }

  private void runTests(int delay, FileSystem fs, Path dir)
      throws IOException, InterruptedException {
    Path baz = dir.resolve(fs.getPath("baz"));
    dir.resolve(fs.getPath("README")).createFile(FILE_ATTRS);
    baz.createDirectory(DIR_ATTRS);
    baz.resolve(fs.getPath("boo.cc")).createFile(FILE_ATTRS);
    baz.resolve(fs.getPath("boo.h")).createFile(FILE_ATTRS);
    DirectoryHooks dh = new DirectoryHooks(dir);
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
    Writer w = new OutputStreamWriter(out, "UTF-8");
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
    noo.createDirectory(DIR_ATTRS);
    Path footxt = noo.resolve(fs.getPath("foo.txt"));
    footxt.createFile(FILE_ATTRS);
    assertChanged(q, delay, footxt);

    // Recreate the deleted file
    README.createFile(FILE_ATTRS);
    assertChanged(q, delay, README);
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

  private static void rmTree(Path p) {
    Files.walkFileTree(p, new FileVisitor<Path>() {
      public FileVisitResult postVisitDirectory(Path p, IOException ex) {
        if (ex != null) { ex.printStackTrace(); }
        try {
          p.delete();
        } catch (IOException ex2) {
          ex2.printStackTrace();
        }
        return FileVisitResult.CONTINUE;
      }

      public FileVisitResult preVisitDirectory(Path p) {
        return FileVisitResult.CONTINUE;
      }

      public FileVisitResult preVisitDirectoryFailed(Path p, IOException ex) {
        return FileVisitResult.CONTINUE;
      }

      public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
        if (!attrs.isDirectory()) {
          try {
            p.deleteIfExists();
          } catch (IOException ex) {
            ex.printStackTrace();
          }
        }
        return FileVisitResult.CONTINUE;
      }

      public FileVisitResult visitFileFailed(Path p, IOException ex) {
        ex.printStackTrace();
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
