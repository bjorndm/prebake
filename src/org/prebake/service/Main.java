package org.prebake.service;

import org.prebake.channel.Commands;
import org.prebake.channel.JsonSource;
import org.prebake.core.MessageQueue;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * An executable class that hooks the Prebakery to the real file system and
 * network, and starts it running.
 *
 * @author mikesamuel@gmail.com
 */
public final class Main {
  public static final void main(String[] argv) {
    MessageQueue mq = new MessageQueue();
    if (argv.length == 1 && argv[0].matches("^-+(?:[?hH]|help)$")) {
      dumpUsage(System.out);
      System.exit(0);
    }
    Config config = new CommandLineConfig(FileSystems.getDefault(), mq, argv);
    if (mq.hasErrors()) {
      dumpUsage(System.err);
      System.err.println();
      for (String msg : mq.getMessages()) {
        System.err.println(msg);
      }
      System.exit(-1);
    }
    Logger logger = Logger.getLogger(Main.class.getName());
    final Prebakery pb = new Prebakery(config, logger) {
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
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                  InputStream in = sock.getInputStream();
                  byte[] buf = new byte[4096];
                  for (int n; (n = in.read(buf)) >= 0;) {
                    bytes.write(buf, 0, n);
                  }
                } finally {
                  sock.close();
                }
                String commandText = bytes.toString("UTF-8");
                try {
                  JsonSource src = new JsonSource(
                      new StringReader(commandText));
                  q.put(Commands.fromJson(getFileSystem(), src));
                } catch (InterruptedException ex) {
                  continue;
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

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() { pb.close(); }
    }));

    pb.start(new Runnable() {
      public void run() { System.exit(0); }
    });
  }

  private static void dumpUsage(PrintStream out) {
    out.println(
        "Usage: prebakery --root <dir> [--ignore <pattern>] [--tools <dirs>]");
    out.println(
        "       [--umask <octal>] [<plan-file> ...]");
    out.println();
  }
}
