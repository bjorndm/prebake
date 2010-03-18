package org.prebake.fs;

import java.nio.file.Path;
import java.nio.file.StubFileSystem;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class DirectoryHooksTest extends TestCase {
  public final void testInit() throws Exception {
    StubFileSystem fs = new StubFileSystem();
    Path p = fs.getPath("/foo/bar");
    fs.getPath("/foo/bar/baz").createDirectory();
    fs.getPath("/foo/bar/baz/boo.cc").createFile();
    fs.getPath("/foo/bar/baz/boo.h").createFile();
    fs.getPath("/foo/bar/README").createFile();
    DirectoryHooks dh = new DirectoryHooks(p);
    dh.start();
    BlockingQueue<Path> q = dh.getUpdates();
    assertEquals(
        "/foo/bar/baz/boo.cc", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertEquals(
        "/foo/bar/baz/boo.h", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertEquals(
        "/foo/bar/README", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertNull(q.poll(500, TimeUnit.MILLISECONDS));
  }
}
