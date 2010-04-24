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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
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
    Logger logger = Logger.getLogger(Main.class.getName());
    CommandLineArgs args = new CommandLineArgs(argv);
    // TODO: should we use the global logger here since that's the one with the
    // handlers?
    if (!CommandLineArgs.setUpLogger(args, logger)) {
      System.out.println(USAGE);
      System.exit(0);
    }

    if (!args.getFlags().isEmpty()
        || !args.getValues().isEmpty()) {
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

    Bake bake = new Bake(logger) {

      @Override
      protected Connection connect(int port) throws IOException {
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
      protected void launch(String... argv) throws IOException {
        new ProcessBuilder(argv).inheritIO().start();
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
      Commands commands = bake.decodeArgv(cwd, argv);
      result = bake.issueCommands(prebakeDir, commands, System.out);
    } catch (IOException ex) {
      ex.printStackTrace();
      result = -1;
    }
    System.exit(result);
  }

  public static final String USAGE = (
      ""
      + "bake [-v | -vv | -q | -qq | --logLevel=<level]\n"
      + "\n"
      + "    -qq         extra quiet : errors only\n"
      + "    -q          quiet : warnings and errors only\n"
      + "    -v          verbose\n"
      + "    -vv         extra verbose\n"
      + "    --logLevel  see java.util.logging.Level for value names");
}
