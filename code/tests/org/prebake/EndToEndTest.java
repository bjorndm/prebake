// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.prebake;

import org.prebake.channel.Commands;
import org.prebake.client.Bake;
import org.prebake.client.Connection;
import org.prebake.fs.StubPipe;
import org.prebake.js.JsonSource;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubOperatingSystem;
import org.prebake.service.Config;
import org.prebake.service.HighLevelLog;
import org.prebake.service.Logs;
import org.prebake.service.Prebakery;
import org.prebake.service.TestLogHydra;
import org.prebake.util.MoreAsserts;
import org.prebake.util.PbTestCase;
import org.prebake.util.TestClock;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EndToEndTest extends PbTestCase {
  private @Nullable Tester tester;

  @Before public final void setUp() {
    tester = new Tester();
  }

  @After public final void cleanup() {
    tester.close();
    tester = null;
  }

  @Test public final void testClientAndService() throws Exception {
    tester.withFileSystem(fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    root/",
            "      src/",
            "        a.foo \"foo\"",
            "      plan.js \"({ foo: tools.cp('src/*.foo', 'out/*.bar') })\"",
            "  tools/",
            "  tmpdir/")))
        .start()
        .withClientWorkingDir("root")
        .sendCommand("sync")
        .assertResult(0)
        .sendCommand("bake", "foo")
        .assertResult(0)
        .sendCommand("shutdown")
        .assertResult(0)
        .waitForShutdown()
        .assertLog(
            "INFO:Plan file /cwd/root/plan.js is up to date",
            "INFO:Cooking foo",
            "INFO:Starting bake of product foo",
            "INFO:Product up to date: foo",
            "INFO:Cooked foo")
        .assertFileSystem(
            "/",
            "  cwd/",
            "    root/",
            "      src/",
            "        a.foo \"foo\"",
            "      plan.js \"...\"",
            "      .prebake/",
            "        logs/",
            "        cmdline \"...\"",
            "      out/",
            "        a.bar \"foo\"",
            "  tools/",
            "  tmpdir/");
  }

  @Test public final void testParameterizedProduct() throws Exception {
    // Normally, you would not use parameterized rules with cp, but this is
    // for a test.
    tester.withFileSystem(fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    root/",
            "      src/",
            "        a.foo \"A\"",
            "        b.foo \"B\"",
            "        c.foo \"C\"",
            "        d.foo \"D\"",
            "      plan.js \"({p:tools.cp('src/*(x).foo', 'out/*(x).bar')})\"",
            "  tools/",
            "  tmpdir/")))
        .start()
        .withClientWorkingDir("root")
        .sendCommand("sync")
        .assertResult(0)
        .sendCommand("bake", "p[\"x\":\"c\"]")
        .assertResult(0)
        .sendCommand("shutdown")
        .assertResult(0)
        .waitForShutdown()
        .assertLog(
            "INFO:Plan file /cwd/root/plan.js is up to date",
            "INFO:Cooking p[\"x\":\"c\"]",
            "INFO:Starting bake of product p[\"x\":\"c\"]",
            "INFO:Product up to date: p[\"x\":\"c\"]",
            "INFO:Cooked p[\"x\":\"c\"]")
        .assertFileSystem(
            "/",
            "  cwd/",
            "    root/",
            "      src/",
            "        a.foo \"A\"",
            "        b.foo \"B\"",
            "        c.foo \"C\"",
            "        d.foo \"D\"",
            "      plan.js \"...\"",
            "      .prebake/",
            "        logs/",
            "        cmdline \"...\"",
            "      out/",
            "        c.bar \"C\"",
            "  tools/",
            "  tmpdir/");
  }

  // TODO: test recovery from a syntactically malformed Bakefile.

  private class Tester {
    /** Directory used for DB files. */
    private File tempDir;
    /** Queue between service and client. */
    private BlockingQueue<Commands> commandQueue;
    /** Used to schedule future tasks. */
    private ScheduledThreadPoolExecutor execer
        = new ScheduledThreadPoolExecutor(16);
    /** An in-memory file-system. */
    private FileSystem fs;
    /** The service under test. */
    private Prebakery service;
    /** Issues commands to the service. */
    private Bake client;
    private Logs logs;
    /** The working directory for the client. */
    private Path clientWorkingDir;
    /** Invoked when the service shuts down. */
    private final OnClose onClose = new OnClose();
    /** The result of the last command the client issued. */
    private int commandResult;
    /**
     * A stream to which output received by the client from the service is
     * written.
     */
    private final ByteArrayOutputStream clientOut = new ByteArrayOutputStream();

    Tester withFileSystem(FileSystem fs) {
      this.fs = fs;
      return this;
    }

    Tester start() throws IOException {
      Logger logger = getLogger(Level.INFO);
      final Config config = new Config() {
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
        public int getWwwPort() { return -1; }
        public boolean getLocalhostTrusted() { return false; }
      };
      OperatingSystem os = new StubOperatingSystem(fs, logger);

      client = new Bake(logger) {
        @Override
        protected Connection connect(int port) {
          assertEquals(1234, port);

          return new Connection() {
            private final ByteArrayOutputStream clientBytes
                = new ByteArrayOutputStream();
            private final StubPipe pipe = new StubPipe(1024);
            private final OutputStream connOut
                = new FilterOutputStream(clientBytes) {
                  boolean closed = false;
                  @Override public synchronized void close()
                      throws IOException {
                    if (!closed) {
                      clientBytes.close();
                      closed = true;
                      byte[] commandBytes = clientBytes.toByteArray();
                      Commands commands = Commands.fromJson(
                          config.getClientRoot(),
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
        protected void launch(Path prebakeDir, List<String> argv) {
          throw new UnsupportedOperationException("NOT NEEDED FOR THIS TEST");
        }

        @Override
        protected void sleep(int millis) {
          throw new UnsupportedOperationException("NOT NEEDED FOR THIS TEST");
        }
      };

      TestClock clock = new TestClock();

      TestLogHydra logHydra = new TestLogHydra(
          logger, fs.getPath("/cwd/root/.prebake/logs"), clock);
      logHydra.install();

      HighLevelLog highLevelLog = new HighLevelLog(clock);

      logs = new Logs(highLevelLog, logger, logHydra);

      service = new Prebakery(
          config, getCommonJsEnv(), execer, os, logs) {
            @Override
            protected Environment createDbEnv(Path dir) {
              EnvironmentConfig envConfig = new EnvironmentConfig();
              envConfig.setAllowCreate(true);
              tempDir = Files.createTempDir();
              return new Environment(tempDir, envConfig);
            }

            @Override protected String makeToken() { return "super-secret"; }

            @Override
            protected int openChannel(int portHint, BlockingQueue<Commands> q) {
              commandQueue = q;
              return portHint == 0 ? 1234 : portHint;
            }

            @Override
            protected Map<String, String> getSysEnv() {
              return ImmutableMap.of();
            }

            @Override
            protected Map<?, ?> getSysProps() {
              return ImmutableMap.of();
            }
          };

      service.start(onClose);
      // Should be ready for connections now.

      return this;
    }

    Tester withClientWorkingDir(String cwd) {
      clientWorkingDir = fs.getPath(cwd).toAbsolutePath();
      return this;
    }

    Tester sendCommand(String... argv) {
      int result;
      try {
        Path prebakeDir = client.findPrebakeDir(clientWorkingDir);
        Commands commands = client.decodeArgv(prebakeDir.getParent(), argv);
        result = client.issueCommands(prebakeDir, commands, clientOut);
      } catch (IOException ex) {
        ex.printStackTrace();
        result = -1;
      }
      commandResult = result;
      return this;
    }

    Tester assertResult(int expectedResult) {
      assertEquals(expectedResult, commandResult);
      return this;
    }

    Tester waitForShutdown() throws InterruptedException {
      synchronized (onClose) {
        while (!onClose.closed) {
          onClose.wait();
        }
      }
      return this;
    }

    Tester assertLog(String... expectedLog) {
      MoreAsserts.assertContainsInOrder(
          new String(clientOut.toByteArray(), Charsets.UTF_8).split("\n"),
          expectedLog);
      return this;
    }

    Tester assertFileSystem(String... expectedFsAsciiArt) {
      try {
        // Log files come out in an unpredictable order, so skip them.
        for (Path p : fs.getPath("/cwd/root/.prebake/logs")
                 .newDirectoryStream()) {
          p.delete();
        }
      } catch (IOException ex) {
        Throwables.propagate(ex);
      }
      assertEquals(
          Joiner.on('\n').join(expectedFsAsciiArt),
          fileSystemToAsciiArt(fs, 40).trim());
      return this;
    }

    void close() {
      if (service != null) {
        service.close();
        service = null;
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
      if (tempDir != null) {
        rmDirTree(tempDir);
        tempDir = null;
      }
    }
  }
}

final class OnClose implements Runnable {
  boolean closed = false;
  public synchronized void run() {
    closed = true;
    this.notifyAll();
  }
}
