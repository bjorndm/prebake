package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.channel.JsonSource;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

import junit.framework.TestCase;

public class CommandTest extends TestCase {
  private FileSystem fs;

  @Override
  protected void setUp() {
    fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
  }

  @Override
  protected void tearDown() { fs = null; }

  public final void testBuildCommand() throws IOException {
    Command.BuildCommand c = new Command.BuildCommand(
        new LinkedHashSet<String>(Arrays.asList("foo", "bar")));
    assertEquals("[\"build\",{},\"foo\",\"bar\"]", c.toString());
    reparse(c);
  }

  public final void testChangesCommand() throws IOException {
    Command.ChangesCommand c = new Command.ChangesCommand();
    assertEquals("[\"changes\",{}]", c.toString());
    reparse(c);
  }

  public final void testFilesChangedCommand() throws IOException {
    Command.FilesChangedCommand c = new Command.FilesChangedCommand(
        new LinkedHashSet<Path>(Arrays.asList(
            fs.getPath("/foo/bar/baz"), fs.getPath("/boo"))));
    assertEquals(
        "[\"files_changed\",{},\"/foo/bar/baz\",\"/boo\"]", c.toString());
    reparse(c);
  }

  public final void testGraphCommand() throws IOException {
    Command.GraphCommand c = new Command.GraphCommand(
        new LinkedHashSet<String>(Arrays.asList("foo", "bar")));
    assertEquals("[\"graph\",{},\"foo\",\"bar\"]", c.toString());
    reparse(c);
  }

  public final void testHandshakeCommand() throws IOException {
    Command.HandshakeCommand c = new Command.HandshakeCommand("t0K3n");
    assertEquals("[\"handshake\",{},\"t0K3n\"]", c.toString());
    assertEquals("t0K3n", c.token);
    reparse(c);
  }

  public final void testPlanCommand() throws IOException {
    Command.PlanCommand c = new Command.PlanCommand(
        new LinkedHashSet<String>(Arrays.asList("bar", "foo")));
    assertEquals("[\"plan\",{},\"bar\",\"foo\"]", c.toString());
    reparse(c);
  }

  public final void testShutdownCommand() throws IOException {
    Command.ShutdownCommand c = new Command.ShutdownCommand();
    assertEquals("[\"shutdown\",{}]", c.toString());
    reparse(c);
  }

  public final void testSyncCommand() throws IOException {
    Command.SyncCommand c = new Command.SyncCommand();
    assertEquals("[\"sync\",{}]", c.toString());
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
