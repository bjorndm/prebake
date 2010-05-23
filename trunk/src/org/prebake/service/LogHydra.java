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

package org.prebake.service;

import org.prebake.channel.FileNames;
import org.prebake.util.Clock;
import org.prebake.util.SyncLinkedList;
import org.prebake.util.SyncListElement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Handler;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.io.Flushables;

/**
 * A many headed <a href="http://en.wikipedia.org/wiki/Tee_(command)">tee</a>
 * that sits on top of stdout and stderr and redirects the output to log files
 * for whatever operations are happening.
 * <p>
 * Prebake kicks off JavaScript interpreters to build tools and execute OS
 * processes.  These will fail intermittently due to JavaScript errors, errors
 * in source files, operating system resource limitations, errors in prebake,
 * and cosmic rays.
 * <p>
 * To help diagnose problems, prebake centralizes log collection.  For each
 * {@link org.prebake.fs.NonFileArtifact}, prebake maintains a log file under
 * the {@link FileNames#LOGS logs} directory.
 * <p>
 * Any operation that would change the validity of an artifact must make sure
 * its logs are recorded in a file named after the artifact.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class LogHydra {
  /** Sources of data that might need to make it to a log file. */
  public static enum DataSource {
    /** stdout and stderr. */
    INHERITED_FILE_DESCRIPTORS,
    /** the prebakery logger. */
    SERVICE_LOGGER,
    ;
  }

  private final Map<String, Head> inhHeads = Maps.newHashMap();
  private final Map<String, Head> loggerHeads = Maps.newHashMap();
  private final SyncLinkedList<Head> inhQ = new SyncLinkedList<Head>();
  private final SyncLinkedList<Head> loggerQ = new SyncLinkedList<Head>();

  private static final class Head extends SyncListElement<Head> {
    final String artifactDescriptor;
    final long timeout;
    final OutputStream out;
    int byteQuota;

    Head(String artifactDescriptor, OutputStream out, int byteQuota,
         long timeout) {
      this.artifactDescriptor = artifactDescriptor;
      this.out = out;
      this.byteQuota = byteQuota;
      this.timeout = timeout;
    }
  }

  private final Path logDir;
  private final Clock clock;

  /** The newly created hydra still needs to be {@link #install installed}. */
  public LogHydra(Path logDir, Clock clock) {
    this.logDir = logDir;
    this.clock = clock;
  }

  /**
   * Installs the inheritedProcessStreams, usually by doing something like
   * {@link System#setOut} and {@link System#setErr}, and
   * {@link Logger#addHandler}.
   * @param wrappedInheritedProcessStreams typically wrappers around
   *   {@code System.out} and {@code System.err}.
   * @param logHandler the log handler to install.
   */
  protected abstract void doInstall(
      OutputStream[] wrappedInheritedProcessStreams, Handler logHandler);

  /**
   * Should only be called once per instance.
   * @param inheritedProcessStreams typically {@code System.out} and
   *     {@code System.err}.
   */
  public void install(OutputStream... inheritedProcessStreams) {
    int n = inheritedProcessStreams.length;
    OutputStream[] wrappedInheritedProcessStreams = new OutputStream[n];
    for (int i = 0; i < n; ++i) {
      final OutputStream inherited = inheritedProcessStreams[i];
      OutputStream wrapped = new OutputStream() {
        @Override
        public void write(int b) {
          try {
            inherited.write(b);
          } catch (IOException ex) {
            // ignore
          }
          Head h = inhQ.first();
          if (h != null) {
            multicastBytes(h, new byte[] { (byte) b }, 0, 1);
          }
        }
        @Override
        public void write(byte[] bytes, int off, int len) {
          try {
            inherited.write(bytes, off, len);
          } catch (IOException ex) {
            // ignore
          }
          multicastBytes(inhQ.first(), bytes, off, len);
        }

        private void multicastBytes(Head h, byte[] bytes, int off, int len) {
          for (; h != null; h = inhQ.after(h)) {
            sendBytes(h, bytes, off, len);
          }
        }
      };
      wrappedInheritedProcessStreams[i] = wrapped;
    }
    Handler logHandler = new Handler() {
      @Override
      public void close() throws SecurityException {
        for (Head h = loggerQ.first(); h != null; h = loggerQ.after(h)) {
          artifactProcessingEnded(h.artifactDescriptor);
        }
      }

      @Override
      public void flush() {
        for (Head h = loggerQ.first(); h != null; h = loggerQ.after(h)) {
          try {
            h.out.flush();
          } catch (IOException ex) {
            artifactProcessingEnded(h.artifactDescriptor);
          }
        }
      }

      @Override
      public void publish(LogRecord r) {
        Head h = loggerQ.first();
        if (h != null) {
          ByteArrayOutputStream bout = new ByteArrayOutputStream();
          OutputStreamWriter out = new OutputStreamWriter(bout, Charsets.UTF_8);
          LogRecordWriter w = new LogRecordWriter(out);
          w.writeRecord(r);
          Flushables.flushQuietly(out);
          byte[] logBytes = bout.toByteArray();
          int n = logBytes.length;
          for (; h != null; h = loggerQ.after(h)) {
            sendBytes(h, logBytes, 0, n);
          }
        }
      }
    };
    doInstall(wrappedInheritedProcessStreams, logHandler);
  }

  private void sendBytes(Head h, byte[] bytes, int off, int len) {
    boolean valid = h.timeout < 0 || clock.nanoTime() < h.timeout;
    if (valid) {
      try {
        synchronized (h) {  // Lock on byteQuota
          if (h.byteQuota < 0) {
            h.out.write(bytes, off, len);
          } else {
            int hlen = h.byteQuota;
            if (hlen > len) { hlen = len; }
            h.out.write(bytes, off, hlen);
            h.byteQuota -= hlen;
            if (h.byteQuota <= 0) {
              h.out.close();
              valid = false;
            }
          }
        }
      } catch (IOException ex) {
        valid = false;
      }
    }
    if (!valid) {
      artifactProcessingEnded(h.artifactDescriptor);
    }
  }

  private static final int BYTE_QUOTA = 1 << 17;  // 100K
  private static final long LIFETIME_NANOS = 30 * 60 * 1000000000L;  // 30 min.

  /** Adds a head to the hydra. */
  public void artifactProcessingStarted(
      String artifactDescriptor, EnumSet<DataSource> data)
      throws IOException {
    artifactProcessingStarted(
        artifactDescriptor, data, BYTE_QUOTA, LIFETIME_NANOS);
  }

  /** Adds a head to the hydra. */
  public void artifactProcessingStarted(
      String artifactDescriptor, EnumSet<DataSource> data,
      int quota, long lifetimeNanos)
      throws IOException {
    if (data.isEmpty()) { return; }
    Map<String, Head> headMap;
    SyncLinkedList<Head> headList;
    if (data.contains(DataSource.INHERITED_FILE_DESCRIPTORS)) {
      headList = inhQ;
      headMap = inhHeads;
    } else {
      headList = loggerQ;
      headMap = loggerHeads;
    }
    long timeout = lifetimeNanos < 0 ? -1 : clock.nanoTime() + lifetimeNanos;
    synchronized (headList) {
      if (headMap.containsKey(artifactDescriptor)) { return; }
      OutputStream out = logDir.resolve(artifactDescriptor + ".log")
          .newOutputStream(
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
      Head h = new Head(artifactDescriptor, out, quota, timeout);
      headMap.put(artifactDescriptor, h);
      headList.add(h);
    }
  }

  /** Kills a hydra head. */
  public void artifactProcessingEnded(String artifactAddress) {
    Head h;
    synchronized (inhQ) {
      h = inhHeads.remove(artifactAddress);
      if (h != null) { inhQ.remove(h); }
    }
    if (h == null) {
      synchronized (loggerQ) {
        h = loggerHeads.remove(artifactAddress);
        if (h != null) { loggerQ.remove(h); }
      }
      if (h == null) { return; }
    }
    Closeables.closeQuietly(h.out);
  }
}
