package org.prebake.util;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;

import junit.framework.TestCase;

/**
 * Ack!  Our test case utilities are complicated enough to warrant tests.
 */
public class PbTestCaseTest extends TestCase {
  public final void testFileSystemFromAsciiArt() throws IOException {
    PbTestCase tc = new PbTestCase() { /* concrete */ };
    FileSystem fs = tc.fileSystemFromAsciiArt(
        "/foo/",
        Joiner.on('\n').join(
            "/",
            "  foo/",
            "    boo.txt (r) \"Howdy!\"",
            "  bar/",
            "    baz.txt \"Hello, World!\"",
            "    far.txt"));
    for (String path : new String[] {
           "/", "/foo", "/foo/boo.txt", "/bar", "/bar/baz.txt", "/bar/far.txt"
         }) {
      assertTrue("'" + path + "'", fs.getPath(path).exists());
    }
    assertTrue(
        fs.getPath("/foo")
            .getFileAttributeView(BasicFileAttributeView.class).readAttributes()
            .isDirectory());
    assertTrue(
        fs.getPath("/foo/boo.txt")
            .getFileAttributeView(BasicFileAttributeView.class).readAttributes()
            .isRegularFile());
    if (false) {  // perms not implemented in stub FS
    assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE),
        fs.getPath("/foo")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    assertEquals(
        ImmutableSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE),
        fs.getPath("/bar/baz.txt")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    assertEquals(
        ImmutableSet.of(PosixFilePermission.OWNER_READ),
        fs.getPath("/foo/boo.txt")
            .getFileAttributeView(PosixFileAttributeView.class).readAttributes()
            .permissions());
    }
    InputStream in = fs.getPath("/bar/baz.txt").newInputStream();
    String out;
    try {
      out = new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
    } finally {
      in.close();
    }
    assertEquals("Hello, World!", out);
    fs.close();
  }
}
