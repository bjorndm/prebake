package org.prebake.fs;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.prebake.service.StubFileSystemProvider;

import junit.framework.TestCase;

public class DirectoryHooksTest extends TestCase {
  public final void testInit() throws Exception {
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
    assertEquals(
        "/foo/bar/baz/boo.cc", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertEquals(
        "/foo/bar/baz/boo.h", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertEquals(
        "/foo/bar/README", q.poll(500, TimeUnit.MILLISECONDS).toString());
    assertNull(q.poll(500, TimeUnit.MILLISECONDS));
  }
}
