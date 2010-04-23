package org.prebake;

import org.prebake.channel.Commands;
import org.prebake.client.Bake;
import org.prebake.client.Connection;
import org.prebake.fs.StubPipe;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubOperatingSystem;
import org.prebake.service.Config;
import org.prebake.service.Prebakery;
import org.prebake.util.PbTestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import org.junit.After;
import org.junit.Test;

public class EndToEndTest extends PbTestCase {
  private File tempDir;
  private BlockingQueue<Commands> commandQueue;
  private ScheduledThreadPoolExecutor execer;
  private FileSystem fs;
  private Prebakery service;

  public static final String CP_TOOL_JS = JsonSink.stringify(
      ""
      + "({ \n"
      + "  fire: function fire(opts, inputs, product, action, exec) { \n"
      // Infer outputs from inputs
      + "    var outGlob = action.outputs[0]; \n"
      + "    var inGlob = action.inputs[0]; \n"
      + "    var xform = glob.xformer(action.inputs, action.outputs); \n"
      + "    for (var i = 0, n = inputs.length; i < n; ++i) { \n"
      + "      var input = inputs[i]; \n"
      + "      var output = xform(input); \n"
      + "      exec('cp', input, output); \n"
      + "    } \n"
      + "  } \n"
      + "})");

  @After public void cleanup() {
    if (service != null) {
      service.close();
      service = null;
    }
    if (tempDir != null) {
      rmDirTree(tempDir);
      tempDir = null;
    }
    if (commandQueue != null) {
      commandQueue.clear();
      commandQueue = null;
    }
    if (execer != null) {
      execer.shutdown();
      execer = null;
    }
    if (fs != null) {
      Closeables.closeQuietly(fs);
      fs = null;
    }
  }

  @Test public final void testClientAndService() throws Exception {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    root/",
            "      src/",
            "        a.foo \"foo\"",
            ("      plan.js \"({ foo: tools.cp('src/*.foo', 'out/*.bar') })\""),
            "  tools/",
            "    cp.js " + JsonSink.stringify(CP_TOOL_JS),
            "  tmpdir/"));

    Logger logger = getLogger(Level.INFO);

    execer = new ScheduledThreadPoolExecutor(16);
    Config config = new Config() {
      public Path getClientRoot() { return fs.getPath("/cwd/root"); }
      public Pattern getIgnorePattern() { return null; }
      public String getPathSeparator() { return ":"; }
      public Set<Path> getPlanFiles() {
        return ImmutableSet.of(fs.getPath("/cwd/root/plan.js"));
      }
      public List<Path> getToolDirs() {
        return ImmutableList.of(fs.getPath("/tools"));
      }
      public int getUmask() { return 700; }
    };
    OperatingSystem os = new StubOperatingSystem(fs, logger);

    Bake client = new Bake(logger) {
      @Override
      protected Connection connect(int port) throws IOException {
        assertEquals(1234, port);

        return new Connection() {
          private final ByteArrayOutputStream clientBytes
              = new ByteArrayOutputStream();
          private final StubPipe pipe = new StubPipe(1024);
          private final OutputStream connOut
              = new FilterOutputStream(clientBytes) {
                boolean closed = false;
                @Override public synchronized void close() throws IOException {
                  if (!closed) {
                    clientBytes.close();
                    closed = true;
                    byte[] commandBytes = clientBytes.toByteArray();
                    Commands commands = Commands.fromJson(
                        fs,
                        new JsonSource(new StringReader(
                            new String(commandBytes, Charsets.UTF_8))),
                        pipe.out);
                    try {
                      commandQueue.put(commands);
                    } catch (InterruptedException ex) {
                      throw new IOException(ex);
                    }
                  }
                }
              };

          public InputStream getInputStream() throws IOException {
            if (pipe.isClosed()) { throw new IOException(); }
            return pipe.in;
          }

          public OutputStream getOutputStream() throws IOException {
            if (pipe.isClosed()) { throw new IOException(); }
            return connOut;
          }

          public void close() throws IOException { pipe.close(); }
        };
      }

      @Override
      protected void launch(String... argv) {
        throw new UnsupportedOperationException("NOT NEEDED FOR THIS TEST");
      }

      @Override
      protected void sleep(int millis) {
        throw new UnsupportedOperationException("NOT NEEDED FOR THIS TEST");
      }
    };

    service = new Prebakery(
        config, getCommonJsEnv(), execer, os, logger) {
          @Override
          protected Environment createDbEnv(Path dir) throws IOException {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            tempDir = Files.createTempDir();
            return new Environment(tempDir, envConfig);
          }

          @Override protected String makeToken() { return "super-secret"; }

          @Override
          protected int openChannel(int portHint, BlockingQueue<Commands> q)
              throws IOException {
            commandQueue = q;
            return portHint == -1 ? 1234 : portHint;
          }
        };

    final class OnClose implements Runnable {
      boolean closed = false;
      public synchronized void run() {
        closed = true;
        this.notifyAll();
      }
    }
    OnClose onClose = new OnClose();
    service.start(onClose);
    // Should be ready for connections now.

    ByteArrayOutputStream clientOut = new ByteArrayOutputStream();

    boolean ok = false;
    try {
      Path clientWorkingDir = fs.getPath("root").toAbsolutePath();

      int result;
      try {
        Path prebakeDir = client.findPrebakeDir(clientWorkingDir);
        Commands commands = client.decodeArgv(clientWorkingDir, "sync");
        result = client.issueCommands(prebakeDir, commands, clientOut);
      } catch (IOException ex) {
        ex.printStackTrace();
        result = -1;
      }
      assertEquals(0, result);

      try {
        Path prebakeDir = client.findPrebakeDir(clientWorkingDir);
        Commands commands = client.decodeArgv(clientWorkingDir, "bake", "foo");
        result = client.issueCommands(prebakeDir, commands, clientOut);
      } catch (IOException ex) {
        ex.printStackTrace();
        result = -1;
      }
      assertEquals(0, result);

      try {
        Path prebakeDir = client.findPrebakeDir(clientWorkingDir);
        Commands commands = client.decodeArgv(clientWorkingDir, "shutdown");
        result = client.issueCommands(prebakeDir, commands, clientOut);
      } catch (IOException ex) {
        ex.printStackTrace();
        result = -1;
      }
      assertEquals(0, result);

      synchronized (onClose) {
        while (!onClose.closed) {
          onClose.wait();
        }
      }
      assertTrue(onClose.closed);

      // TODO: make this less brittle.  We shouldn't disallow extra logging.
      assertEquals(
          Joiner.on('\n').join(
              "INFO:Cooking foo",
              "INFO:Starting bake of product foo",
              "INFO:Running cp with [src/a.foo, out/a.bar]",
              "INFO:Cooked foo",
              ""),
          new String(clientOut.toByteArray(), Charsets.UTF_8));

      assertEquals(
          Joiner.on('\n').join(
              "/",
              "  cwd/",
              "    root/",
              "      src/",
              "        a.foo \"foo\"",
              "      plan.js \"...\"",
              "      .prebake/",
              "        cmdline \"...\"",
              "      out/",
              "        a.bar \"foo\"",
              "  tools/",
              "    cp.js \"...\"",
              "  tmpdir/",
              ""),
          fileSystemToAsciiArt(fs, 40));
      ok = true;
    } finally {
      if (!ok) {
        System.err.println(fileSystemToAsciiArt(fs, 40));
      }
    }
  }
}
