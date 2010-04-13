package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.js.JsonSource;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;

import static junit.framework.Assert.assertEquals;

public class CommandTest {
  private FileSystem fs;

  @Before
  public void setUp() {
    fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
  }

  @After
  public void tearDown() { fs = null; }

  @Test public final void testBuildCommand() throws IOException {
    Command.BuildCommand c = new Command.BuildCommand(ImmutableSet.of(
        "foo", "bar"));
    assertEquals("[\"build\",{},\"foo\",\"bar\"]", c.toString());
    reparse(c);
  }

  @Test public final void testFilesChangedCommand() throws IOException {
    Command.FilesChangedCommand c = new Command.FilesChangedCommand(
        ImmutableSet.of(fs.getPath("/foo/bar/baz"), fs.getPath("/boo")));
    assertEquals(
        "[\"files_changed\",{},\"/foo/bar/baz\",\"/boo\"]", c.toString());
    reparse(c);
  }

  @Test public final void testGraphCommand() throws IOException {
    Command.GraphCommand c = new Command.GraphCommand(ImmutableSet.of(
        "foo", "bar"));
    assertEquals("[\"graph\",{},\"foo\",\"bar\"]", c.toString());
    reparse(c);
  }

  @Test public final void testHandshakeCommand() throws IOException {
    Command.HandshakeCommand c = new Command.HandshakeCommand("t0K3n");
    assertEquals("[\"handshake\",{},\"t0K3n\"]", c.toString());
    assertEquals("t0K3n", c.token);
    reparse(c);
  }

  @Test public final void testPlanCommand() throws IOException {
    Command.PlanCommand c = new Command.PlanCommand(ImmutableSet.of(
        "bar", "foo"));
    assertEquals("[\"plan\",{},\"bar\",\"foo\"]", c.toString());
    reparse(c);
  }

  @Test public final void testShutdownCommand() throws IOException {
    Command.ShutdownCommand c = new Command.ShutdownCommand();
    assertEquals("[\"shutdown\",{}]", c.toString());
    reparse(c);
  }

  @Test public final void testSyncCommand() throws IOException {
    Command.SyncCommand c = new Command.SyncCommand();
    assertEquals("[\"sync\",{}]", c.toString());
    reparse(c);
  }

  @Test public final void testToolHelpCommand() throws IOException {
    Command.ToolHelpCommand c = new Command.ToolHelpCommand();
    assertEquals("[\"tool_help\",{}]", c.toString());
    reparse(c);
  }

  private Command reparse(Command c) throws IOException {
    Command c2 = Command.fromJson(jss(c.toString()), fs);
    assertEquals(c2.getClass(), c.getClass());
    assertEquals(c2.toString(), c.toString());
    return c2;
  }

  private JsonSource jss(String s) {
    return new JsonSource(new StringReader(s));
  }
}
