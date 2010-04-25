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

package org.prebake.service;

import org.prebake.channel.Commands;
import org.prebake.core.MessageQueue;
import org.prebake.js.CommonEnvironment;
import org.prebake.js.JsonSource;
import org.prebake.os.OperatingSystem;
import org.prebake.util.CommandLineArgs;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Executors;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An executable class that hooks the Prebakery to the real file system and
 * network, and starts it running.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Main {
  public static final void main(String[] argv) {
    final Logger logger = Logger.getLogger(Main.class.getName());
    CommandLineArgs args = new CommandLineArgs(argv);
    if (!CommandLineArgs.setUpLogger(args, logger)) {
      System.out.println(USAGE);
      System.exit(0);
    }

    MessageQueue mq = new MessageQueue();
    final FileSystem fs = FileSystems.getDefault();
    ImmutableMap<String, ?> env = CommonEnvironment.makeEnvironment(
        getSystemPropertyMap());
    Config config = new CommandLineConfig(fs, mq, args);
    if (mq.hasErrors()) {
      System.err.println(USAGE);
      System.err.println();
      for (String msg : mq.getMessages()) {
        System.err.println(msg);
      }
      System.exit(-1);
    }
    ScheduledExecutorService execer = Executors
        .getExitingScheduledExecutorService(
            new ScheduledThreadPoolExecutor(4));
    OperatingSystem os = new OperatingSystem() {
      public Path getTempDir() {
        return fs.getPath(System.getProperty("java.io.tmpdir"));
      }
      public Process run(Path cwd, String command, String... argv)
          throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(cwd.toString()));
        // TODO: figure out how to get the error and output to the logger/tool.
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        List<String> args = ImmutableList.<String>builder().add(command)
            .add(argv).build();
        pb.command(args);
        return pb.start();
      }
    };
    final Prebakery pb = new Prebakery(config, env, execer, os, logger) {
      @Override
      protected String makeToken() {
        byte[] bytes = new byte[256];
        new SecureRandom().nextBytes(bytes);
        return new BigInteger(bytes).toString(Character.MAX_RADIX);
      }

      FileSystem getFileSystem() {
        return getConfig().getClientRoot().getFileSystem();
      }

      @Override
      protected int openChannel(int portHint, final BlockingQueue<Commands> q)
          throws IOException {
        final ServerSocket ss = new ServerSocket(portHint);
        Thread th = new Thread(new Runnable() {
          public void run() {
            while (true) {
              try {
                boolean closeSock = true;
                final Socket sock = ss.accept();
                // TODO: move sock handling to a worker or use java.nio stuff.
                try {
                  byte[] bytes = ByteStreams.toByteArray(sock.getInputStream());
                  sock.shutdownInput();
                  String commandText = new String(bytes, Charsets.UTF_8);
                  try {
                    q.put(Commands.fromJson(
                        getFileSystem(),
                        new JsonSource(new StringReader(commandText)),
                        sock.getOutputStream()));
                    // Closing sock is now the service's responsibility.
                    closeSock = false;
                  } catch (InterruptedException ex) {
                    continue;
                  }
                } finally {
                  if (closeSock) { sock.close(); }
                }
              } catch (IOException ex) {
                logger.log(Level.WARNING, "Connection failed", ex);
              }
            }
          }
        }, Main.class.getName() + "#command_receiver");
        th.setDaemon(true);
        th.start();
        return ss.getLocalPort();
      }

      @Override
      protected Environment createDbEnv(Path dir) {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        return new Environment(new File(dir.toUri()), envConfig);
      }
    };

    // If an interrupt signal is received,
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() { pb.close(); }
    }));

    final boolean[] exitMutex = new boolean[1];
    synchronized (exitMutex) {
      // Start the prebakery with a handler that will cause the main thread to
      // complete when it receives a shutdown command or is programatically
      // closed.
      pb.start(new Runnable() {
        public void run() {
          // When a shutdown command is received, signal the main thread so
          // it can complete.
          synchronized (exitMutex) {
            exitMutex[0] = true;
            exitMutex.notifyAll();
          }
        }
      });
      // The loop below gets findbugs to shut up about a wait outside a loop.
      while (!exitMutex[0]) {
        try {
          exitMutex.wait();
        } catch (InterruptedException ex) {
          // Just exit below if the main thread is interrupted.
          break;
        }
      }
    }
    System.exit(0);
  }

  public static final String USAGE = (
      ""
      + "Usage: prebakery --root <dir> [--ignore <pattern>] [--tools <dirs>]\n"
      + "       [--umask <octal>] [<plan-file> ...]\n"
      + "       [-v | -vv | -q | -qq | --logLevel=<level]");

  private static Map<String, String> getSystemPropertyMap() {
    ImmutableMap.Builder<String, String> sysProps = ImmutableMap.builder();
    for (Map.Entry<?, ?> e : System.getProperties().entrySet()) {
      sysProps.put((String) e.getKey(), (String) e.getValue());
    }
    return sysProps.build();
  }
}
