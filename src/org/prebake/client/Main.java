package org.prebake.client;

import org.prebake.channel.Commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wires the bake command to the file system and network.
 *
 * @author mikesamuel@gmail.com
 */
public final class Main {

  public static void main(String... argv) {
    Logger logger = Logger.getLogger(Main.class.getName());
    {
      int i = 0;
      for (int n = argv.length; i < n; ++i) {
        String arg = argv[i];
        if ("-v".equals(arg)) {
          logger.setLevel(Level.FINE);
        } else if ("--logLevel".equals(arg)) {
          logger.setLevel(Level.parse(argv[++i]));
        } else {
          break;
        }
        // TODO: dump usage and exit on -?, -h, etc.
      }
      String[] unusedArgv = new String[argv.length - i];
      System.arraycopy(argv, i, unusedArgv, 0, unusedArgv.length);
      argv = unusedArgv;
    }

    Bake bake = new Bake(logger) {

      @Override
      Connection connect(int port) throws IOException {
        final Socket socket = new Socket(InetAddress.getLocalHost(), port);
        return new Connection() {
          public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
          }

          public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
          }

          public void close() throws IOException {
            socket.close();
          }
        };
      }

      @Override
      void launch(String... argv) throws IOException {
        new ProcessBuilder(argv).inheritIO().start();
      }

      @Override
      void sleep(int millis) throws InterruptedException {
        Thread.sleep(millis);
      }
    };
    Path cwd = FileSystems.getDefault().getPath(System.getProperty("user.dir"));
    int result;
    try {
      Path prebakeDir = bake.findPrebakeDir(cwd);
      Commands commands = bake.decodeArgv(cwd, argv);
      result = bake.issueCommands(prebakeDir, commands, System.out);
    } catch (IOException ex) {
      ex.printStackTrace();
      result = -1;
    }
    System.exit(result);
  }
}
