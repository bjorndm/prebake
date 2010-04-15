package org.prebake.service.bake;

import org.prebake.fs.StubFileVersioner;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubProcess;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubScheduledExecutorService;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;

import org.junit.Before;
import org.junit.Test;

@ParametersAreNonnullByDefault
public class BakerTest extends PbTestCase {
  private FileSystem fs;
  private OperatingSystem os;

  @Before
  public void init() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd/",
        Joiner.on('\n').join(
            "/",
            "  tmp/",
            "  cwd/",
            "    foo/",
            "      a.txt  \"foo a text\"",
            "      a.html \"foo a html\"",
            "      b.txt  \"foo b text\"",
            "      b.html \"foo b html\"",
            "    bar/",
            "      a.txt  \"bar a text\"",
            "      a.html \"bar a html\"",
            "      b.txt  \"bar b text\"",
            "      b.html \"bar b html\""));
    os = new StubOperatingSystem(fs);
  }

  @Test
  public final void testBake() throws IOException {
    Logger logger = getLogger(Level.INFO);
    StubFileVersioner files = new StubFileVersioner(
        fs.getPath("/cwd"), logger);
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Baker baker = new Baker(os, files, logger, execer);
    fail("IMPLEMENT ME");
  }

  // TODO: actions time out
  // TODO: product invalidated when tool changes
  // TODO: product invalidated when input changes
  // TODO: product invalidated when input created
  // TODO: product invalidated when input deleted
  // TODO: output globs that overlap inputs
  // TODO: process returns error code.
  // TODO: process takes a long time.
  // TODO: changed output is updated

  /**
   * A stub operating system that knows four commands:<table>
   *   <tr><td>cp<td>copy</tr>
   *   <tr><td>cat<td>concatenates inputs to the last argument</tr>
   *   <tr><td>munge<td>appends to each file the reverse of the previous</tr>
   *   <tr><td>bork<td>appends "Bork!" to the end of each argument</r>
   * </ul>
   */
  private static class StubOperatingSystem implements OperatingSystem {
    private final FileSystem fs;
    StubOperatingSystem(FileSystem fs) { this.fs = fs; }

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

    public Process run(final Path cwd, String command, final String... argv)
        throws IOException {
      if (command.equals("cp")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws Exception {
                if (argv.length != 2) { return -1; }
                cwd.resolve(argv[0]).copyTo(fs.getPath(argv[1]));
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
                    try {
                      byte[] bytes = ByteStreams.toByteArray(in);
                      for (int n = bytes.length / 2, i = n / 2; --i >= 0;) {
                        byte b = bytes[i];
                        bytes[i] = bytes[n - i - 1];
                        bytes[n - i - 1] = b;
                      }
                      out.write(bytes);
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
