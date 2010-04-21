package org.prebake.client;

import org.prebake.channel.Command;
import org.prebake.channel.Commands;
import org.prebake.channel.FileNames;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;
import org.prebake.service.Prebakery;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Sends commands to a {@link Prebakery} to drive builds.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public abstract class Bake {
  private final Logger logger;

  public Bake(Logger logger) { this.logger = logger; }

  protected abstract Connection connect(int port) throws IOException;
  protected abstract void launch(String... argv) throws IOException;
  protected abstract void sleep(int millis) throws InterruptedException;

  Path findPrebakeDir(Path cwd) throws IOException {
    cwd = cwd.toRealPath(false).normalize();
    Path dirName = cwd.getFileSystem().getPath(FileNames.DIR);
    for (Path d = cwd; d != null; d = d.getParent()) {
      Path dir = d.resolve(dirName);
      logger.log(Level.FINER, "Checking {0}", d);
      if (dir.exists() && (Boolean) dir.getAttribute("isDirectory")) {
        logger.log(Level.FINE, "Found prebake dir {0}", dir);
        return dir;
      }
    }
    throw new IOException(
        "No " + FileNames.DIR + " in an ancestor of " + cwd + "."
        + "  Please run the prebakery first.");
  }

  Commands decodeArgv(Path cwd, String... argv) throws IOException {
    int n = argv.length;
    Command cmd = null;
    if (n != 0) {
      StringBuilder sb = new StringBuilder();
      JsonSink out = new JsonSink(sb);
      out.write("[");
      out.writeValue(argv[0]);
      out.write(",");
      out.write("{");
      out.write("}");
      for (int i = 1; i < n; ++i) {
        out.write(",");
        out.writeValue(argv[i]);
      }
      out.write("]");
      out.close();
      cmd = Command.fromJson(
          new JsonSource(new StringReader(sb.toString())), cwd.getFileSystem());
    }

    List<Command> commands = Lists.newArrayList();
    if (isSubList(Arrays.asList(/*bake*/ "me", "a", "pie"),
                  Arrays.asList(argv))) {
      logger.log(Level.INFO, "Killing Kenny and restarting",
                 new AuthoritahNotRespectedException());
    }
    if (cmd != null) { commands.add(cmd); }
    return Commands.valueOf(commands, null);
  }

  private Connection connectOrSpawn(Path prebakeDir)
      throws IOException {
    int tries = 10;
    boolean started = false;
    while (true) {
      Path portFile = prebakeDir.resolve(FileNames.PORT);
      logger.log(Level.FINER, "Reading port file {0}", portFile);
      String portStr = read(portFile);
      int port;
      try {
        port = Integer.parseInt(portStr);
        if ((port & ~0x0ffff) != 0) {
          logger.log(
              Level.SEVERE, "Bad port number {0}", portStr);
          port = -1;
        }
      } catch (NumberFormatException ex) {
        logger.log(
            Level.SEVERE, "Malformed port {0} in {1}",
            new Object[] { portStr, portFile });
        port = -1;
      }
      try {
        if (port != -1) {
          logger.log(Level.FINE, "Trying to connect to port {0}", port);
          return connect(port);
        }
      } catch (IOException ex) {
        logger.log(Level.SEVERE, "Failed to connect to " + port, ex);
      }
      if (--tries < 0) {
        throw new IOException(
            "Timed out trying to connect to " + port + " per " + portFile);
      }
      if (!started) {
        logger.info("Trying to launch prebakery");
        Path cmdlineFile = prebakeDir.resolve(FileNames.CMD_LINE);
        logger.log(Level.FINE, "Reading {0}", cmdlineFile);
        String content = read(cmdlineFile);
        JsonSource src = new JsonSource(new StringReader(content));
        List<?> args;
        try {
          args = src.nextArray();
        } catch (IOException ex) {
          IOException ioex = new IOException(
              "Can't launch prebakery using malformed arguments in "
              + cmdlineFile + ": " + content);
          ioex.initCause(ex);
          throw ioex;
        }
        String[] argv = new String[args.size()];
        for (int i = argv.length; --i >= 0;) {
          Object o = args.get(i);
          if (!(o instanceof String)) {
            throw new IOException("Malformed argv: " + content);
          }
          argv[i] = (String) o;
        }
        launch(argv);
        started = true;
      }
      logger.fine("Waiting");
      try {
        sleep(500);
      } catch (InterruptedException ex) {
        throw new IOException("Failed to connect");
      }
    }
  }

  int issueCommands(Path prebakeDir, Commands commands, OutputStream out)
      throws IOException {
    Connection conn = connectOrSpawn(prebakeDir);

    Path tokenFile = prebakeDir.resolve(FileNames.TOKEN);
    logger.log(Level.FINER, "Reading token file {0}", tokenFile);
    String token = read(tokenFile);
    {
      List<Command> commandList = Lists.newArrayList();
      commandList.add(new Command.HandshakeCommand(token));
      for (Command command : commands) { commandList.add(command); }
      commands = Commands.valueOf(commandList, null);
    }

    try {
      OutputStream connOut = conn.getOutputStream();
      JsonSink sink = new JsonSink(new OutputStreamWriter(
          connOut, Charsets.UTF_8));
      commands.toJson(sink);
      sink.close();

      byte[] buf = new byte[4096];
      boolean seenOutput = false;
      byte lastByte = -1;  // The last byte is the success code.
      InputStream in = conn.getInputStream();
      for (int n; (n = in.read(buf)) > 0;) {
        if (seenOutput) {
          out.write(lastByte);
        } else {
          seenOutput = true;
        }
        if (n > 1) {
          out.write(buf, 0, n - 1);
        }
        lastByte = buf[n - 1];
      }
      return lastByte;
    } finally {
      conn.close();
    }
  }

  private static String read(Path p) throws IOException {
    Reader r = new InputStreamReader(
        p.newInputStream(StandardOpenOption.READ), Charsets.UTF_8);
    try {
      return CharStreams.toString(r);
    } finally {
      r.close();
    }
  }

  private static boolean isSubList(List<?> a, List<?> cont) {
    int m = a.size();
    // The empty list is a sublist of all lists.
    if (m == 0) { return true; }
    int n = cont.size();
    Object a0 = a.get(0);
    if (a0 == null) {
      for (int i = n - m + 1; --i >= 0;) {
        if (cont.get(i) == null && a.equals(cont.subList(i, i + m))) {
          return true;
        }
      }
    } else {
      for (int i = n - m + 1; --i >= 0;) {
        if (a0.equals(cont.get(i)) && a.equals(cont.subList(i, i + m))) {
          return true;
        }
      }
    }
    return false;
  }

  static final class AuthoritahNotRespectedException extends Exception {
    private AuthoritahNotRespectedException() {
      initCause(new IHateYouSoVeryVeryMuch());
    }
  }

  static final class IHateYouSoVeryVeryMuch extends Exception {
    private IHateYouSoVeryVeryMuch() { /* not public */ }
  }
}
