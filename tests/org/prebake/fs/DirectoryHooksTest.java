package org.prebake.fs;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.prebake.service.StubFileSystemProvider;

import junit.framework.TestCase;

public class DirectoryHooksTest extends TestCase {
  public final void testHooks() throws Exception {
    int delay = 100;
    TimeUnit ms = TimeUnit.MILLISECONDS;

    FileSystem fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
    Path p = fs.getPath("/foo/bar");
    fs.getPath("/foo/bar/baz").createDirectory();
    fs.getPath("/foo/bar/baz/boo.cc").createFile();
    fs.getPath("/foo/bar/baz/boo.h").createFile();
    fs.getPath("/foo/bar/README").createFile();
    DirectoryHooks dh = new DirectoryHooks(p);
    dh.start();
    BlockingQueue<Path> q = dh.getUpdates();
    assertEquals("/foo/bar/baz/boo.cc", "" + poll(q, delay, ms));
    assertEquals("/foo/bar/baz/boo.h", "" + poll(q, delay, ms));
    assertEquals("/foo/bar/README", "" + poll(q, delay, ms));
    assertNull(poll(q, delay, ms));

    // Create a file
    fs.getPath("/foo/bar/baz/boo2.cc").createFile();
    assertEquals("/foo/bar/baz/boo2.cc", "" + poll(q, delay, ms));
    assertNull(poll(q, delay, ms));

    // Modify a file
    OutputStream out = fs.getPath("/foo/bar/baz/boo.cc").newOutputStream();
    Writer w = new OutputStreamWriter(out, "UTF-8");
    w.write("\nprintf(\"Hello, World!\\n\");\n");
    w.close();
    assertEquals("/foo/bar/baz/boo.cc", "" + poll(q, delay, ms));
    assertNull(poll(q, delay, ms));

    // Delete a file
    fs.getPath("/foo/bar/README").deleteIfExists();
    fs.getPath("/foo/bar/LICENSE").deleteIfExists();  // does not exist
    assertEquals("/foo/bar/README", "" + poll(q, delay, ms));
    assertNull(poll(q, delay, ms));

    // Create a directory and put a file in it.
    fs.getPath("/foo/bar/noo").createDirectory();
    fs.getPath("/foo/bar/noo/foo.txt").createFile();
    assertEquals("/foo/bar/noo/foo.txt", "" + poll(q, delay, ms));
    assertEquals(null, poll(q, delay, ms));

    // Recreate the deleted file
    fs.getPath("/foo/bar/README").createFile();
    assertEquals("/foo/bar/README", "" + poll(q, delay, ms));
    assertEquals(null, poll(q, delay, ms));
  }

  private static <T> T poll(BlockingQueue<T> q, int delay, TimeUnit u)
      throws InterruptedException {
    return q.poll(delay, u);
  }
}
