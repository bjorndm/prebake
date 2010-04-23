package org.prebake.client;

import org.prebake.channel.Commands;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class BakeTest extends PbTestCase {

  @Test public final void testEverythingWorksFirstTime() throws IOException {
    new BakeTestRunner()
        .withCwd("/foo/bar/baz/boo")
        .withFile("/foo/bar/.prebake/port", "1234")
        .withFile("/foo/bar/.prebake/token", "S3cR37")
        .withArgv("bake", "this", "that")
        .withResponse("OK\0")
        .expectConnect(1234, false)
        .issue()
        .expectResult(0)
        .expectSent(
            "[[\"handshake\",{},\"S3cR37\"],[\"bake\",{},\"this\",\"that\"]]")
        .expectOutput("OK")
        .expectConnClosed();
  }

  @Test public final void testNoPrebakeDir() {
    try {
      new BakeTestRunner()
          .withCwd("/foo/bar/baz/boo")
          .withArgv("bake", "this", "that");
    } catch (IOException ex) {
      assertEquals(
          "No .prebake in an ancestor of /foo/bar/baz/boo."
          + "  Please run the prebakery first.",
          ex.getMessage());
      return;
    }
    fail();
  }

  @Test public final void testMalformedPort() throws IOException {
    new BakeTestRunner()
        .withCwd("/foo/bar/baz/boo")
        .withFile("/foo/bar/.prebake/port", "123a")
        .withFile("/foo/bar/.prebake/token", "S3cR37")
        .withFile("/foo/bar/.prebake/cmdline",
                  "[\"prebakery\",\"--root=/foo/bar\"]")
        .withArgv("bake", "this", "that")
        .withResponse("OK\0")
        .expectLaunch("prebakery", "--root=/foo/bar")
        .expectSleep(500, false)
        .withFile("/foo/bar/.prebake/port", "789")
        .expectConnect(789, false)
        .issue()
        .expectResult(0)
        .expectSent(
            "[[\"handshake\",{},\"S3cR37\"],[\"bake\",{},\"this\",\"that\"]]")
        .expectOutput("OK")
        .expectConnClosed();
  }

  @Test public final void testMalformedArgv() {
    try {
      new BakeTestRunner()
          .withCwd("/foo/bar/baz/boo")
          .withFile("/foo/bar/.prebake/port", "123a")
          .withFile("/foo/bar/.prebake/token", "S3cR37")
          .withFile("/foo/bar/.prebake/cmdline",
                    "[\"prebakery\",\"--root=/foo/bar")  // string not closed
          .withArgv("bake", "this", "that")
          .issue();
    } catch (IOException ex) {
      assertEquals(
          "Can't launch prebakery using malformed arguments in"
          + " /foo/bar/.prebake/cmdline: [\"prebakery\",\"--root=/foo/bar",
          ex.getMessage());
      return;
    }
    fail();
  }

  @Test public final void testNoToken() throws IOException {
    try {
      new BakeTestRunner()
          .withCwd("/foo/bar/baz/boo")
          .withFile("/foo/bar/.prebake/port", "1234")
          .withArgv("bake", "this", "that")
          .expectConnect(1234, false)
          .issue();
    } catch (FileNotFoundException ex) {
      assertEquals("/foo/bar/.prebake/token", ex.getMessage());
      return;
    }
    fail();
  }

  @Test public final void testNeedToLaunch() throws IOException {
    new BakeTestRunner()
        .withCwd("/foo/bar/baz/boo")
        .withFile("/foo/bar/.prebake/port", "1234")
        .withFile("/foo/bar/.prebake/token", "S3cR37")
        .withFile("/foo/bar/.prebake/cmdline",
                  "[\"prebakery\",\"--root=/foo/bar\"]")
        .withArgv("bake", "this", "that")
        .withResponse("OK\0")
        // Client fails to connect at old port.
        .expectConnect(1234, true)
        // Tries to launch a prebakery.
        .expectLaunch("prebakery", "--root=/foo/bar")
        // Waits for it to start up.
        .expectSleep(500, false)
        // The prebakery starts and writes out a new port and token.
        .withFile("/foo/bar/.prebake/port", "789")
        .withFile("/foo/bar/.prebake/token", "70K3n")
        // Client tries to connect again at new port and succeeds.
        .expectConnect(789, false)
        .issue()
        .expectResult(0)
        // And sends over the updated token.
        .expectSent(
            "[[\"handshake\",{},\"70K3n\"],[\"bake\",{},\"this\",\"that\"]]")
        .expectOutput("OK")
        .expectConnClosed();
  }

  @Test public final void testLaunchSlowly() throws IOException {
    new BakeTestRunner()
        .withCwd("/foo/bar/baz/boo")
        .withFile("/foo/bar/.prebake/port", "1234")
        .withFile("/foo/bar/.prebake/token", "S3cR37")
        .withFile("/foo/bar/.prebake/cmdline",
                  "[\"prebakery\",\"--root=/foo/bar\"]")
        .withArgv("bake", "this", "that")
        .withResponse("OK\0")
        .expectConnect(1234, true)
        .expectLaunch("prebakery", "--root=/foo/bar")
        // Keeps trying to connect to the old port waiting a bit between tries.
        .expectSleep(500, false)
        .expectConnect(1234, true)
        .expectSleep(500, false)
        .expectConnect(1234, true)
        .expectSleep(500, false)
        // Finally prebakery starts up and writes out a new port file and token.
        .withFile("/foo/bar/.prebake/port", "789")
        .withFile("/foo/bar/.prebake/token", "70K3n")
        // Client connects successfully to the new port.
        .expectConnect(789, false)
        .issue()
        .expectResult(0)
        // and sends the new token.
        .expectSent(
            "[[\"handshake\",{},\"70K3n\"],[\"bake\",{},\"this\",\"that\"]]")
        .expectOutput("OK")
        .expectConnClosed();
  }

  private interface Action {
    public void run() throws IOException;
  }

  private static class Expectation {
    final String type;
    final Object value;
    final Object result;
    final List<Action> actions = Lists.newArrayList();

    Expectation(String type, Object value, Object result) {
      this.type = type;
      this.value = value;
      this.result = result;
    }

    void runAll() throws IOException {
      for (Action action : actions) { action.run(); }
    }
  }

  private final class BakeTestRunner {
    private final List<Expectation> expectations = Lists.newLinkedList();
    private Path cwd;
    private Path prebakeDir;
    private Commands commands;
    private ByteArrayInputStream connIn;
    private ByteArrayOutputStream connOut;
    private ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int result;
    private boolean connClosed;

    private Bake bake = new Bake(Logger.getLogger(getName())) {
      @Override
      protected Connection connect(int port) throws IOException {
        Expectation exp = expectations.remove(0);
        assertEquals("conn", exp.type);
        assertEquals(port, exp.value);
        exp.runAll();
        if (Boolean.TRUE.equals(exp.result)) {
          return new Connection() {
            public InputStream getInputStream() throws IOException {
              if (connClosed) { throw new IOException(); }
              return connIn;
            }

            public OutputStream getOutputStream() throws IOException {
              if (connClosed) { throw new IOException(); }
              return connOut = new ByteArrayOutputStream();
            }

            public void close() { connClosed = true; }
          };
        } else {
          throw new IOException();
        }
      }

      @Override
      protected void launch(String... argv) throws IOException {
        Expectation exp = expectations.remove(0);
        assertEquals("launch", exp.type);
        assertEquals(Arrays.asList((String[]) exp.value), Arrays.asList(argv));
        exp.runAll();
      }

      @Override
      protected void sleep(int millis) throws InterruptedException {
        Expectation exp = expectations.remove(0);
        assertEquals("sleep", exp.type);
        assertEquals(exp.value, Integer.valueOf(millis));
        try {
          exp.runAll();
        } catch (IOException ex) {
          throw new IOError(ex);
        }
        if (!Boolean.TRUE.equals(exp.result)) {
          throw new InterruptedException();
        }
      }
    };

    BakeTestRunner withCwd(String cwd) throws IOException {
      FileSystem fs;
      try {
        fs = new StubFileSystemProvider("mfs").newFileSystem(
            new URI("mfs", null, null, 0, "/", null, "#" + cwd), null);
      } catch (URISyntaxException ex) {
        Throwables.propagate(ex);
        return this;
      }
      this.cwd = fs.getPath(cwd);
      mkdirs(this.cwd);
      return this;
    }

    BakeTestRunner withFile(final String path, final String content)
        throws IOException {
      Action a = new Action() {
        public void run() throws IOException {
          Path p = cwd.getFileSystem().getPath(path);
          mkdirs(p.getParent());
          writeFile(p, content);
        }
      };
      if (expectations.isEmpty()) {
        a.run();
      } else {
        expectations.get(expectations.size() - 1).actions.add(a);
      }
      return this;
    }

    BakeTestRunner withArgv(String... argv) throws IOException {
      this.prebakeDir = bake.findPrebakeDir(this.cwd);
      this.commands = bake.decodeArgv(cwd, argv);
      return this;
    }

    BakeTestRunner expectSleep(int millis, boolean interrupted) {
      expectations.add(new Expectation("sleep", millis, !interrupted));
      return this;
    }

    BakeTestRunner expectConnect(int port, boolean fails) {
      expectations.add(new Expectation("conn", port, !fails));
      return this;
    }

    BakeTestRunner expectConnClosed() {
      assertTrue(connClosed);
      return this;
    }

    BakeTestRunner expectLaunch(String... argv) {
      expectations.add(new Expectation("launch", argv, null));
      return this;
    }

    BakeTestRunner expectSent(String s) {
      assertEquals(s, new String(connOut.toByteArray(), Charsets.UTF_8));
      return this;
    }

    BakeTestRunner withResponse(String s) {
      assertFalse(connClosed);
      connIn = new ByteArrayInputStream(s.getBytes(Charsets.UTF_8));
      return this;
    }

    BakeTestRunner issue() throws IOException {
      result = bake.issueCommands(prebakeDir, commands, out);
      return this;
    }

    BakeTestRunner expectResult(int result) {
      assertEquals(result, this.result);
      assertTrue(expectations.toString(), expectations.isEmpty());
      return this;
    }

    BakeTestRunner expectOutput(String content) {
      assertEquals(content, new String(out.toByteArray(), Charsets.UTF_8));
      return this;
    }
  }
}
