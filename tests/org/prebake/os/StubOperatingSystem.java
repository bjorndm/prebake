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

package org.prebake.os;

import org.prebake.fs.FilePerms;

import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

/**
 * A stub operating system that knows four commands:<table>
 *   <tr><td>cp<td>copy</tr>
 *   <tr><td>cat<td>concatenates inputs to the last argument</tr>
 *   <tr><td>ls<td>list files</tr>
 *   <tr><td>munge<td>appends to each file the reverse of the previous</tr>
 *   <tr><td>bork<td>appends "Bork!" to the end of each argument</tr>
 * </ul>
 */
public final class StubOperatingSystem implements OperatingSystem {
  private final FileSystem fs;
  private final Logger logger;

  // TODO: possibly consolidate this code with that in OsProcessTest.

  public StubOperatingSystem(FileSystem fs, Logger logger) {
    this.fs = fs;
    this.logger = logger;
  }

  public PipeFlusher getPipeFlusher() {
    throw new UnsupportedOperationException();
  }

  public Path getTempDir() {
    Path p = fs.getPath("/tmpdir");
    if (p.notExists()) {
      try {
        p.createDirectory();
      } catch (IOException ex) {
        throw new IOError(ex);
      }
    }
    return p;
  }

  private static void mkdirs(Path p) throws IOException {
    if (p.exists()) { return; }
    Path parent = p.getParent();
    if (parent != null) { mkdirs(parent); }
    p.createDirectory(FilePerms.perms(0700, true));
  }

  public OsProcess run(final Path cwd, String command, final String... argv) {
    logger.log(
        Level.INFO, "Running {0} with {1}",
        new Object[] { command, Arrays.asList(argv) });
    return new OsProcessImpl(cwd, command, argv);
  }

  final class OsProcessImpl extends OsProcess {
    private StubProcess p;
    private Path cwd;
    private String command;
    private String[] argv;

    OsProcessImpl(Path cwd, String command, String[] argv) {
      super(StubOperatingSystem.this, cwd, command, argv);
    }

    @Override
    protected void combineStdoutAndStderr() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected boolean hasStartedRunning() {
      return p != null;
    }

    @Override
    protected void preemptivelyKill() {
      cwd = null;
      command = null;
    }

    @Override
    protected void setWorkdirAndCommand(Path cwd, String cmd, String... argv) {
      this.cwd = cwd;
      this.command = cmd;
      this.argv = argv;
    }

    @Override
    protected Process startRunning(
        boolean inheritOutput, boolean closeInput,
        @Nullable Path outFile, boolean truncateOutput, @Nullable Path inFile,
        ImmutableMap<String, String> env, boolean inheritEnv)
        throws IOException {
      return this.p = makeStubProcess(cwd, command, argv);
    }

    private StubProcess makeStubProcess(
        final Path cwd, String command, final String[] argv)
        throws IOException {
      if (command.equals("cp")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws Exception {
                if (argv.length != 2) { return -1; }
                Path out = cwd.resolve(argv[1]);
                mkdirs(out.getParent());
                Path from = cwd.resolve(argv[0]);
                from.copyTo(out);
                return 0;
              }
            });
      } else if (command.equals("cat")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                OutputStream out = cwd.resolve(argv[argv.length - 1])
                    .newOutputStream(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                  for (int inp = 0; inp < argv.length - 1; ++inp) {
                    InputStream in = cwd.resolve(argv[inp]).newInputStream();
                    try {
                      ByteStreams.copy(in, out);
                    } finally {
                      in.close();
                    }
                  }
                } finally {
                  out.close();
                }
                return 0;
              }
            });
      } else if (command.equals("ls")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                OutputStream out = cwd.resolve(argv[argv.length - 1])
                    .newOutputStream(
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
                try {
                  for (int i = 0; i < argv.length - 1; ++i) {
                    out.write(argv[i].getBytes(Charsets.UTF_8));
                    out.write('\n');
                  }
                } finally {
                  out.close();
                }
                return 0;
              }
            });
      } else if (command.equals("munge")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                OutputStream out = cwd.resolve(argv[argv.length - 1])
                    .newOutputStream(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                  for (int inp = 0; inp < argv.length - 1; ++inp) {
                    InputStream in = cwd.resolve(argv[inp]).newInputStream();
                    byte[] bytes;
                    try {
                      bytes = ByteStreams.toByteArray(in);
                    } finally {
                      in.close();
                    }
                    for (int n = bytes.length, i = n / 2; --i >= 0;) {
                      byte b = bytes[i];
                      bytes[i] = bytes[n - i - 1];
                      bytes[n - i - 1] = b;
                    }
                    out.write(bytes);
                  }
                } finally {
                  out.close();
                }
                return 0;
              }
            });
      } else if (command.equals("bork")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                for (String arg : argv) {
                  OutputStream out = cwd.resolve(arg).newOutputStream(
                      StandardOpenOption.CREATE,
                      StandardOpenOption.TRUNCATE_EXISTING);
                  Writer w = new OutputStreamWriter(out, Charsets.UTF_8);
                  try {
                    w.write("Bork!");
                  } finally {
                    w.close();
                  }
                }
                return 0;
              }
            });
      } else {
        throw new FileNotFoundException(command);
      }
    }
  }
}