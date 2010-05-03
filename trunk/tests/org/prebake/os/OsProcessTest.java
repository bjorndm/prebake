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

import org.prebake.fs.StubPipe;
import org.prebake.util.PbTestCase;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OsProcessTest extends PbTestCase {
  private ScheduledExecutorService execer;
  private FileSystem fs;
  private OperatingSystem os;

  @Before public void setUp() throws IOException {
    execer = new ScheduledThreadPoolExecutor(8);
    fs = fileSystemFromAsciiArt(
        "/cwd",
        "/",
        "  cwd/",
        "    foo \"foo\\nbar\\nbaz\\nboo\\nfar\\nfaz\"",
        "    baz \"BAZ\"");
    os = new OperatingSystem() {
      PipeFlusher fl;

      public synchronized PipeFlusher getPipeFlusher() {
        if (fl == null) {
          fl = new PipeFlusher(execer);
          fl.start();
        }
        return fl;
      }

      public Path getTempDir() { return fs.getPath("/tmp"); }

      public OsProcess run(Path cwd, String command, String... argv) {
        return new InVmProcess(this, cwd, command, argv);
      }
    };
  }

  @After public void tearDown() {
    if (os != null) {
      Closeables.closeQuietly(os.getPipeFlusher());
      os = null;
    }
    if (execer != null) {
      execer.shutdown();
      execer = null;
    }
    if (fs != null) {
      Closeables.closeQuietly(fs);
      fs = null;
    }
  }

  @Test(timeout=1000)
  public final void testWriteTo() throws Exception {
    OsProcess p = os.run(fs.getPath("/cwd"), "cat", "foo", "baz")
        .writeTo(fs.getPath("bar")).run();
    assertEquals(0, p.waitFor());
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    foo \"foo\\nbar\\nbaz\\nboo\\nfar\\nfaz\"",
            "    baz \"BAZ\"",
            "    bar \"foo\\nbar\\nbaz\\nboo\\nfar\\nfazBAZ\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test(timeout=1000) public final void testReadFrom() throws Exception {
    OsProcess p = os.run(fs.getPath("/cwd"), "tr", "ao", "oa")
        .readFrom(fs.getPath("foo")).writeTo(fs.getPath("bar")).run();
    assertEquals(0, p.waitFor());
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    foo \"foo\\nbar\\nbaz\\nboo\\nfar\\nfaz\"",
            "    baz \"BAZ\"",
            "    bar \"faa\\nbor\\nboz\\nbaa\\nfor\\nfoz\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test(timeout=1000)
  public final void testPipeTo() throws Exception {
    OsProcess p = os.run(fs.getPath("/cwd"), "sort").writeTo(fs.getPath("bar"))
        .run();
    os.run(fs.getPath("/cwd"), "tr", "ao", "oa")
        .readFrom(fs.getPath("foo")).pipeTo(p).run();
    assertEquals(0, p.waitFor());
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    foo \"foo\\nbar\\nbaz\\nboo\\nfar\\nfaz\"",
            "    baz \"BAZ\"",
            "    bar \"baa\\nbor\\nboz\\nfaa\\nfor\\nfoz\"",
          ""),
    fileSystemToAsciiArt(fs, 40));
  }

  private final class InVmProcess extends OsProcess {
    private boolean hasStartedRunning;
    private Callable<Integer> action;
    private StubPipe inPipe = new StubPipe(128);
    private StubPipe outPipe = new StubPipe(128);

    InVmProcess(OperatingSystem os, Path cwd, String cmd, String... argv) {
      super(os, cwd, cmd, argv);
    }

    @Override
    protected void combineStdoutAndStderr() {
      assert !hasStartedRunning;
    }

    @Override
    protected boolean hasStartedRunning() {
      return hasStartedRunning;
    }

    @Override
    protected void preemptivelyKill() {
      action = new Callable<Integer>() {
        public Integer call() throws Exception { return -1; }
      };
    }

    @Override
    protected void setWorkdirAndCommand(
        final Path cwd, final String command, final String... argv) {
      Callable<Integer> action;
      if ("cat".equals(command)) {
        action = new Callable<Integer>() {
          public Integer call() throws Exception {
            try {
              if (argv.length == 0) {
                try {
                  ByteStreams.copy(inPipe.in, outPipe.out);
                } finally {
                  inPipe.in.close();
                }
              } else {
                inPipe.in.close();
                for (String arg : argv) {
                  Path p = cwd.resolve(arg);
                  InputStream fin = p.newInputStream();
                  try {
                    ByteStreams.copy(fin, outPipe.out);
                  } finally {
                    fin.close();
                  }
                }
              }
            } finally {
              outPipe.out.close();
            }
            return 0;
          }
        };
      } else if ("tr".equals(command)) {
        action = new Callable<Integer>() {
          String src = argv[0], tgt = argv[1];
          int n = tgt.length();
          public Integer call() throws Exception {
            Reader r = new InputStreamReader(inPipe.in, Charsets.UTF_8);
            try {
              Writer w = new OutputStreamWriter(outPipe.out, Charsets.UTF_8);
              try {
                for (int chi; (chi = r.read()) >= 0;) {
                  char ch = (char) chi;
                  int idx = src.indexOf(ch);
                  if (idx < n) {
                    w.write(idx < 0 ? ch : tgt.charAt(idx));
                  }
                }
              } finally {
                w.close();
              }
            } finally {
              r.close();
            }
            return 0;
          }
        };
      } else if ("sort".equals(command)) {
        action = new Callable<Integer>() {
          public Integer call() throws Exception {
            BufferedReader r = new BufferedReader(
                new InputStreamReader(inPipe.in, Charsets.UTF_8));
            List<String> lines = Lists.newArrayList();
            try {
              for (String line; (line = r.readLine()) != null;) {
                lines.add(line);
              }
            } finally {
              r.close();
            }
            Collections.sort(lines);
            Writer w = new OutputStreamWriter(outPipe.out, Charsets.UTF_8);
            try {
              Joiner.on('\n').appendTo(w, lines);
            } finally {
              w.close();
            }
            return 0;
          }
        };
      } else {
        action = new Callable<Integer>() {
          public Integer call() throws Exception {
            throw new FileNotFoundException(command);
          }
        };
      }
      this.action = action;
    }

    @Override
    protected Process startRunning(
        boolean inheritOutput, boolean closeInput,
        final Path outFile, final Path inFile) throws IOException {
      assert !hasStartedRunning;
      hasStartedRunning = true;
      final Future<Integer> result = execer.submit(action);
      if (closeInput) { inPipe.in.close(); }
      if (inFile != null) {
        execer.submit(new Runnable() {
          public void run() {
            try {
              InputStream fin = inFile.newInputStream();
              try {
                ByteStreams.copy(fin, inPipe.out);
              } finally {
                try {
                  fin.close();
                } finally {
                  inPipe.out.close();
                }
              }
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          }
        });
      }
      if (outFile != null) {
        execer.submit(new Runnable() {
          public void run() {
            try {
              OutputStream fout = outFile.newOutputStream(
                  StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING);
              try {
                ByteStreams.copy(outPipe.in, fout);
              } finally {
                try {
                  fout.close();
                } finally {
                  outPipe.in.close();
                }
              }
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          }
        });
      }
      return new Process() {
        @Override
        public void destroy() {
          result.cancel(true);
          Closeables.closeQuietly(inPipe);
          Closeables.closeQuietly(outPipe);
        }

        @Override
        public int exitValue() {
          if (!result.isDone()) {
            throw new IllegalThreadStateException();
          }
          try {
            return result.get().intValue();
          } catch (ExecutionException ex) {
            Throwables.propagate(ex);
            return -1;
          } catch (InterruptedException ex) {
            Throwables.propagate(ex);
            return -1;
          }
        }

        @Override
        public InputStream getErrorStream() {
          return new InputStream() {
            @Override
            public int read() throws IOException { return -1; }
          };
        }

        @Override
        public InputStream getInputStream() {
          return outPipe.in;
        }

        @Override
        public OutputStream getOutputStream() {
          return inPipe.out;
        }

        @Override
        public int waitFor() throws InterruptedException {
          try {
            return result.get();
          } catch (ExecutionException ex) {
            return -1;
          }
        }
      };
    }
  }
}
