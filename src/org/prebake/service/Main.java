package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.channel.JsonSource;
import org.prebake.core.MessageQueue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;

/**
 * An executable class that hooks the Prebakery to the real file system and
 * network, and starts it running.
 *
 * @author mikesamuel@gmail.com
 */
public final class Main {
  public static final void main(String[] argv) {
    // TODO: handle -?, --help, -h, and variants by printing usage data.
    MessageQueue mq = new MessageQueue();
    Config config = new CommandLineConfig(FileSystems.getDefault(), mq, argv);
    if (mq.hasErrors()) {
      for (String msg : mq.getMessages()) {
        System.err.println(msg);
      }
      System.exit(-1);
    }
    new Prebakery(config) {
      @Override
      protected String makeToken() {
        byte[] bytes = new byte[256];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
          sb.append("0123456789abcdef".charAt(b >>> 4))
             .append("0123456789abcdef".charAt(b & 0xf));
        }
        return sb.toString();
      }

      FileSystem getFileSystem() {
        return getConfig().getClientRoot().getFileSystem();
      }

      @Override
      protected int openChannel(int portHint, final BlockingQueue<Command> q)
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
                  src.expect("[");
                  if (!src.check("]")) {
                    FileSystem fs = getFileSystem();
                    do {
                      q.put(Command.fromJson(src, fs));
                    } while (src.check(","));
                    src.expect("]");
                  }
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
    }.start(new Runnable() {
      public void run() { System.exit(0); }
    });
  }
}
