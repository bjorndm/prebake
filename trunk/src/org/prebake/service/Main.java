package org.prebake.service;

import org.prebake.channel.Commands;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSource;
import org.prebake.os.OperatingSystem;
import org.prebake.util.CommandLineArgs;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Executors;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An executable class that hooks the Prebakery to the real file system and
 * network, and starts it running.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Main {
  public static final void main(String[] argv) {
    Logger logger = Logger.getLogger(Main.class.getName());
    CommandLineArgs args = new CommandLineArgs(argv);
    if (!CommandLineArgs.setUpLogger(args, logger)) {
      System.out.println(USAGE);
      System.exit(0);
    }

    if (!args.getFlags().isEmpty() || !args.getValues().isEmpty()) {
      System.err.println(USAGE);
      if (!args.getFlags().isEmpty()) {
        System.err.println(
            "Unused flags : " + Joiner.on(' ').join(args.getFlags()));
      }
      if (!args.getValues().isEmpty()) {
        System.err.println(
            "Unused values : " + Joiner.on(' ').join(args.getValues()));
      }
      System.exit(-1);
    }

    MessageQueue mq = new MessageQueue();
    Config config = new CommandLineConfig(FileSystems.getDefault(), mq, args);
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
        throw new Error("IMPLEMENT ME");
      }
      public Process run(Path cwd, String command, String... argv) {
        throw new Error("IMPLEMENT ME");
      }
    };
    final Prebakery pb = new Prebakery(config, execer, os, logger) {
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
                Socket sock = ss.accept();
                // TODO: move sock handling to a worker or use java.nio stuff.
                byte[] bytes;
                try {
                  bytes = ByteStreams.toByteArray(sock.getInputStream());
                  String commandText = new String(bytes, Charsets.UTF_8);
                  try {
                    q.put(Commands.fromJson(
                        getFileSystem(),
                        new JsonSource(
                            new StringReader(commandText)),
                        new OutputStreamWriter(
                            sock.getOutputStream(), Charsets.UTF_8)));
                  } catch (InterruptedException ex) {
                    continue;
                  }
                } finally {
                  sock.close();
                }
              } catch (IOException ex) {
                ex.printStackTrace();
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

    final Object exitMutex = new Object();
    synchronized (exitMutex) {
      // Start the prebakery with a handler that will cause the main thread to
      // complete when it receives a shutdown command or is programatically
      // closed.
      pb.start(new Runnable() {
        public void run() {
          // When a shutdown command is received, signal the main thread so
          // it can complete.
          synchronized (exitMutex) { exitMutex.notify(); }
        }
      });
      // The loop below gets findbugs to shut up about a wait outside a loop.
      for (boolean needToWait = true; needToWait; needToWait = false) {
        try {
          exitMutex.wait();
        } catch (InterruptedException ex) {
          // just exit below if the main thread is interrupted
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
}
