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

import org.prebake.channel.Commands;
import org.prebake.util.CommandLineArgs;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An executable class that wires the bake command to the file system and
 * network.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Main {

  public static void main(String... argv) {
    long t0 = System.nanoTime();
    final Logger logger = Logger.getLogger(Main.class.getName());
    CommandLineArgs args = new CommandLineArgs(argv);
    if (!CommandLineArgs.setUpLogger(args, logger)) {
      System.out.println(USAGE);
      System.exit(0);
    }

    if (!args.getFlags().isEmpty()) {
      System.err.println(USAGE);
      if (!args.getFlags().isEmpty()) {
        System.err.println(
            "Unused flags : " + Joiner.on(' ').join(args.getFlags()));
      }
      System.exit(-1);
    }

    Bake bake = new Bake(logger) {

      @Override
      protected Connection connect(int port) throws IOException {
        final Socket socket = new Socket(
            InetAddress.getLoopbackAddress(), port);
        return new Connection() {
          public InputStream getInputStream() throws IOException {
            return new FilterInputStream(socket.getInputStream()) {
              @Override public void close() throws IOException {
                socket.shutdownInput();
              }
            };
          }

          public OutputStream getOutputStream() throws IOException {
            return new FilterOutputStream(socket.getOutputStream()) {
              @Override public void close() throws IOException {
                socket.shutdownOutput();
              }
            };
          }

          public void close() throws IOException {
            socket.close();
          }
        };
      }

      @Override
      protected void launch(List<String> argv) throws IOException {
        Map<String, String> env = Maps.newLinkedHashMap();
        boolean doneWithPreJavaFlags = false;
        List<String> cmd = Lists.newArrayListWithCapacity(argv.size() + 1);
        cmd.add("java");
        for (String arg : argv) {
          if (!doneWithPreJavaFlags && arg.startsWith("-Denv.")) {
            // See PATH environment variable channeling in CommandLineArgs
            int eq = arg.indexOf('=');
            env.put(
                arg.substring(6, eq).toUpperCase(Locale.ROOT),
                arg.substring(eq + 1));
          } else {
            cmd.add(arg);
          }
          if (!arg.startsWith("-")) { doneWithPreJavaFlags = true; }
        }
        logger.log(
            Level.FINE, "Execing {0} with env {1}", new Object[] { cmd, env });
        ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[0]));
        pb.environment().putAll(env);
        pb.inheritIO().start();
      }

      @Override
      protected void sleep(int millis) throws InterruptedException {
        Thread.sleep(millis);
      }
    };
    Path cwd = FileSystems.getDefault().getPath(System.getProperty("user.dir"));
    int result;
    try {
      Path prebakeDir = bake.findPrebakeDir(cwd);
      Collection<String> argVals = args.getValues();
      Commands commands = bake.decodeArgv(
          prebakeDir.getParent(), argVals.toArray(new String[argVals.size()]));
      result = bake.issueCommands(prebakeDir, commands, System.out);
    } catch (IOException ex) {
      ex.printStackTrace();
      result = -1;
    }
    logger.log(
        Level.INFO, "Build took {0}.", prettyTime(System.nanoTime() - t0));
    System.exit(result);
  }

  public static final String USAGE = (
      ""
      + "bake [-v | -vv | -q | -qq | --logLevel=<level>]\n"
      + "\n"
      + "    -qq         extra quiet : errors only\n"
      + "    -q          quiet : warnings and errors only\n"
      + "    -v          verbose\n"
      + "    -vv         extra verbose\n"
      + "    --logLevel  see java.util.logging.Level for value names");

  private static final long NANOSECS_PER_SEC = 1000 * 1000 * 1000;
  /** 94,000,000,000 ns => "1 minute, 34 seconds" */
  private static CharSequence prettyTime(long nanos) {
    StringBuilder sb = new StringBuilder();
    long seconds = nanos / NANOSECS_PER_SEC;
    long minutes = seconds / 60;
    seconds -= minutes * 60;
    if (minutes != 0) {
      if (minutes == 1) {
        sb.append("1 minute");
      } else {
        sb.append(minutes).append(" minutes");
      }
    }
    if (seconds != 0 || sb.length() == 0) {
      if (sb.length() != 0) { sb.append(", "); }
      if (seconds == 1) {
        sb.append("1 second");
      } else {
        sb.append(seconds).append(" seconds");
      }
    }
    return sb;
  }
}
