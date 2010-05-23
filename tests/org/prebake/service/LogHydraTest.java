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

import org.prebake.util.PbTestCase;
import org.prebake.util.TestClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.logging.Level;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LogHydraTest extends PbTestCase {
  // These tests do not exercise proper locking, deadlock safety, or any other
  // concurrency issues.  Do not rely on them to highlight such problems.

  private FileSystem fs;
  private TestClock clock;
  private TestLogHydra hydra;
  private ByteArrayOutputStream[] stubStdoutAndStderr
      = new ByteArrayOutputStream[] {
    new ByteArrayOutputStream(),
    new ByteArrayOutputStream(),
  };
  private OutputStream stdout, stderr;

  @Before public final void setUp() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/",
        "/",
        "  logs/");
    clock = new TestClock();
    hydra = new TestLogHydra(getLogger(Level.INFO), fs.getPath("/logs"), clock);
    hydra.install(stubStdoutAndStderr);
    stdout = hydra.wrappedInheritedProcessStreams[0];
    stderr = hydra.wrappedInheritedProcessStreams[1];
  }

  @After public final void tearDown() throws IOException {
    fs.close();
    fs = null;
    clock = null;
    hydra = null;
    stdout = stderr = null;
  }

  @Test public final void testStreamInstallation() throws IOException {
    stdout.write((byte) 'o');
    stdout.write("ut".getBytes(Charsets.UTF_8));
    stderr.write("er".getBytes(Charsets.UTF_8));
    stderr.write((byte) 'r');
    assertEquals(
        "out",
        new String(stubStdoutAndStderr[0].toByteArray(), Charsets.UTF_8));
    assertEquals(
        "err",
        new String(stubStdoutAndStderr[1].toByteArray(), Charsets.UTF_8));
  }

  @Test public final void testOneStreamHead() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS));
    stdout.write("PANIC".getBytes(Charsets.UTF_8));
    hydra.artifactProcessingEnded("foo");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"PANIC\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testOneLoggerHead() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER));
    getLogger(Level.INFO).warning("PANIC");
    hydra.artifactProcessingEnded("foo");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"WARNING:PANIC\\n\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testMultiLoggerHeaded() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER));
    getLogger(Level.INFO).info("Hello");
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER));
    getLogger(Level.INFO).info(", ");
    hydra.artifactProcessingEnded("foo");
    getLogger(Level.INFO).info("World!");
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"INFO:Hello\\nINFO:, \\n\"",
            "    bar.log \"INFO:, \\nINFO:World!\\n\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testMultiStreamHeaded() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS));
    hydra.wrappedInheritedProcessStreams[0].write(
        "Hello".getBytes(Charsets.UTF_8));
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS));
    hydra.wrappedInheritedProcessStreams[1].write(
        ", ".getBytes(Charsets.UTF_8));
    hydra.artifactProcessingEnded("foo");
    hydra.wrappedInheritedProcessStreams[1].write(
        "World".getBytes(Charsets.UTF_8));
    hydra.wrappedInheritedProcessStreams[0].write((byte) '!');
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"Hello, \"",
            "    bar.log \", World!\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testTruncatesExistingLogFile() throws IOException {
    Path oldFile = fs.getPath("/logs/foo.log");
    OutputStream out = oldFile.newOutputStream(StandardOpenOption.CREATE);
    try {
      out.write("old data".getBytes(Charsets.UTF_8));
    } finally {
      out.close();
    }
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"old data\"",
            ""),
        fileSystemToAsciiArt(fs, 40));

    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS));
    stdout.write("PANIC".getBytes(Charsets.UTF_8));
    hydra.artifactProcessingEnded("foo");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"PANIC\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testStreamQuotaLimited() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS),
        10/*B*/, -1 /* no time limit */);
    hydra.wrappedInheritedProcessStreams[0].write(
        "Hello".getBytes(Charsets.UTF_8));
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS),
        10/*B*/, -1 /* no time limit */);
    hydra.wrappedInheritedProcessStreams[1].write(
        ", ".getBytes(Charsets.UTF_8));
    hydra.wrappedInheritedProcessStreams[1].write(
        "World".getBytes(Charsets.UTF_8));
    hydra.wrappedInheritedProcessStreams[0].write((byte) '!');
    hydra.artifactProcessingEnded("foo");
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"Hello, Wor\"",
            "    bar.log \", World!\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testStreamTimeout() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS),
        1024/*B*/, 100/*ns*/);  // timeout=+100
    // Write a portion of a buffer.
    hydra.wrappedInheritedProcessStreams[0].write(
        "HHelloo".getBytes(Charsets.UTF_8), 1, 5);
    clock.advance(50);  // t=+50
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS),
        1024/*B*/, 100/*ns*/);   // timeout=+150
    hydra.wrappedInheritedProcessStreams[1].write(
        ", ".getBytes(Charsets.UTF_8));
    clock.advance(75);  // t=+125 so foo now cut off.
    hydra.wrappedInheritedProcessStreams[1].write(
        "World".getBytes(Charsets.UTF_8));
    hydra.wrappedInheritedProcessStreams[0].write((byte) '!');
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"Hello, \"",
            "    bar.log \", World!\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testLoggerQuotaLimited() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER),
        10/*B*/, -1 /* no time limit */);
    getLogger(Level.INFO).info("X");
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER),
        10/*B*/, -1 /* no time limit */);
    getLogger(Level.INFO).info("Y");
    hydra.artifactProcessingEnded("foo");
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"INFO:X\\nINF\"",
            "    bar.log \"INFO:Y\\n\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }

  @Test public final void testLoggerTimeout() throws IOException {
    hydra.artifactProcessingStarted(
        "foo", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER),
        1024/*B*/, 100/*ns*/);  // timeout=+100
    getLogger(Level.INFO).info("X");
    clock.advance(50);  // t=+50
    hydra.artifactProcessingStarted(
        "bar", EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER),
        1024/*B*/, 100/*ns*/);   // timeout=+150
    getLogger(Level.INFO).info("Y");
    clock.advance(75);  // t=+125 so foo now cut off.
    getLogger(Level.INFO).info("Z");
    hydra.artifactProcessingEnded("bar");
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  logs/",
            "    foo.log \"INFO:X\\nINFO:Y\\n\"",
            "    bar.log \"INFO:Y\\nINFO:Z\\n\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
  }
}
