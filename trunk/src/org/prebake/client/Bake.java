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

package org.prebake.client;

import org.prebake.channel.Command;
import org.prebake.channel.Commands;
import org.prebake.channel.FileNames;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;
import org.prebake.service.Prebakery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import java.io.File;
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
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public abstract class Bake {
  private final Logger logger;

  public Bake(Logger logger) { this.logger = logger; }

  /** Connect to localhost on the given port. */
  protected abstract Connection connect(int port) throws IOException;
  /** Launch a JVM with the following arguments. */
  protected abstract void launch(List<String> argv) throws IOException;
  /** Pause the current thread for the given number of milliseconds. */
  protected abstract void sleep(int millis) throws InterruptedException;

  @VisibleForTesting
  public Path findPrebakeDir(Path cwd) throws IOException {
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

  @VisibleForTesting
  public Commands decodeArgv(Path clientRoot, String... argv)
       throws IOException {
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
          new JsonSource(new StringReader(sb.toString())), clientRoot);
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
        if (!src.isEmpty()) {
          throw new IOException("Unpexpected command line " + src.next());
        }
        ImmutableList.Builder<String> argv = ImmutableList.builder();
        boolean wroteClassName = false;
        for (Object o : args) {
          if (!(o instanceof String)) {
            throw new IOException("Malformed argv: " + content);
          }
          String arg = (String) o;
          if (arg.startsWith("-D")) {
            if (arg.startsWith("-Djava.class.path=")) {
              int eq = arg.indexOf('=');
              String classpath = arg.substring(eq + 1);
              boolean singleJar = classpath.endsWith(".jar")
                  && classpath.contains(File.separator);
              argv.add(singleJar ? "-jar" : "-classpath");
              argv.add(classpath);
              continue;
            }
          } else if (!wroteClassName) {
            argv.add("org.prebake.service.Main");
            wroteClassName = true;
          }
          argv.add(arg);
        }
        if (!wroteClassName) { argv.add("org.prebake.service.Main"); }
        launch(argv.build());
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

  @VisibleForTesting
  public int issueCommands(Path prebakeDir, Commands commands, OutputStream out)
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
      out.write('\n');
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
