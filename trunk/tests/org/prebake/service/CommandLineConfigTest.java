package org.prebake.service;

import org.prebake.core.MessageQueue;
import org.prebake.util.CommandLineArgs;

import com.google.common.base.Joiner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class CommandLineConfigTest extends TestCase {
  public final void testClientRoot() throws IOException {
    Config c;
    c = assertConfig(new String[0], false, "Please specify --root");
    assertEquals("[]", CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root=/" }, false,
        "Plan file /recipe.js is not a file");
    assertEquals(
        "[\"--root\",\"/\",\"/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root", "/" }, false,
        "Plan file /recipe.js is not a file");
    assertEquals(
        "[\"--root\",\"/\",\"/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root", "/foo" }, false,
        "Plan file /foo/recipe.js is not a file");
    assertEquals(
        "[\"--root\",\"/foo\",\"/foo/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(new String[] { "--root", "/foo/bar/project" }, true);
    assertEquals(
        "[\"--root\",\"/foo/bar/project\",\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/recipe.js" },
        true);
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
    assertEquals("[/foo/bar/project/recipe.js]", c.getPlanFiles().toString());
    assertEquals(
        "[\"--root\",\"/foo/bar/project\",\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/recipee.js" },
        false,
        "Plan file /foo/bar/project/recipee.js is not a file");
    assertEquals(
        "[\"--root\",\"/foo/bar/project\",\"/foo/bar/project/recipee.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "--root=/foo" },
        false, "Dupe arg --root");
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
    assertEquals(
        "[\"--root\",\"/foo/bar/project\",\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
  }

  public final void testIgnorePattern() throws IOException {
    Config c;
    c = assertConfig(
        new String[] { "--root=/foo/bar/project", "--ignore=*~" }, false,
        "Dangling meta character '*' near index 0\n*~\n^",
        "Ignore pattern is a regular expression, not a glob");
    assertNull(c.getIgnorePattern());
    c = assertConfig(
        new String[] { "--root=/foo/bar/project", "--ignore=^.*~$" }, true);
    assertEquals("^.*~$", c.getIgnorePattern().pattern());
    assertEquals(Pattern.DOTALL, c.getIgnorePattern().flags());
    assertEquals(
        ""
        + "[\"--root\",\"/foo/bar/project\","
        + "\"--ignore\",\"^.*~$\","
        + "\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root=project", "--ignore=^.*~$", "--ignore=\\.bak$" },
        false, "Dupe arg --ignore");
    assertEquals("^.*~$", c.getIgnorePattern().pattern());
  }

  public final void testUmask() throws IOException {
    Config c;
    c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals(0640, c.getUmask());
    assertEquals(
        ""
        + "[\"--root\",\"/foo/bar/project\","
        + "\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(new String[] { "--root=project", "--umask=750" }, true);
    assertEquals(0750, c.getUmask());
    assertEquals(
        ""
        + "[\"--root\",\"/foo/bar/project\","
        + "\"--umask\",\"750\","
        + "\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] { "--root=project", "--umask=750", "--umask=640" },
        false, "Dupe arg --umask");
    assertEquals(0750, c.getUmask());
    assertConfig(
        new String[] { "--root=project", "--umask=abc" },
        false, "umask abc is not a valid octal number");
    assertConfig(
        new String[] { "--root=project", "--umask=649" },
        false, "umask 649 is not a valid octal number");
    assertConfig(new String[] { "--root=project", "--umask=777" }, true);
    assertConfig(new String[] { "--root=project", "--umask=200" }, true);
    assertConfig(
        new String[] { "--root=project", "--umask=1777" },
        false, "Invalid umask 1777");
    assertConfig(
        new String[] { "--root=project", "--umask=-1" },
        false, "Invalid umask 37777777777");
  }

  public final void testPathSeparator() throws IOException {
    Config c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals(":", c.getPathSeparator());
  }

  public final void testToolPaths() throws IOException {
    Config c;
    c = assertConfig(new String[] { "--root=project" }, true);
    assertTrue(c.getToolDirs().isEmpty());
    c = assertConfig(
        new String[] { "--root", "project", "--tools", "baz/tools" }, false,
        "Tool dir /foo/bar/baz/tools is not a directory");
    c = assertConfig(
        new String[] { "--root", "project", "--tools", "tools" }, true);
    assertEquals("[/foo/bar/tools]", "" + c.getToolDirs());
    c = assertConfig(
        new String[] { "--root", "project", "--tools", "tools:project/ptools" },
        true);
    assertEquals(
        "[/foo/bar/tools, /foo/bar/project/ptools]", "" + c.getToolDirs());
    assertEquals(
        ""
        + "[\"--root\",\"/foo/bar/project\","
        + "\"--tools\",\"/foo/bar/tools:/foo/bar/project/ptools\","
        + "\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] {
            "--root", "project",
            "--tools", "/foo/bar/tools:project/ptools",
            "--tools=tools" },
        false,
        "Dupe tools dir /foo/bar/tools");
    assertEquals(
        "[/foo/bar/tools, /foo/bar/project/ptools]", "" + c.getToolDirs());
  }

  public final void testPlanPaths() throws IOException {
    Config c;
    c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals("[/foo/bar/project/recipe.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] { "--root=project", "project/recipe.js" }, true);
    assertEquals("[/foo/bar/project/recipe.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] { "--root=project", "project/recipe2.js" }, true);
    assertEquals("[/foo/bar/project/recipe2.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] {
            "--root=project", "project/recipe2.js", "project/recipe.js" },
        true);
    assertEquals(
        "[/foo/bar/project/recipe2.js, /foo/bar/project/recipe.js]",
        c.getPlanFiles().toString());
    assertEquals(
        ""
        + "[\"--root\",\"/foo/bar/project\","
        + "\"/foo/bar/project/recipe2.js\","
        + "\"/foo/bar/project/recipe.js\"]",
        CommandLineConfig.toArgv(c));
    c = assertConfig(
        new String[] {
            "--root=project",
            "project/recipe2.js", "project/recipe.js",
            "/foo/bar/project/recipe.js" },
        false,
        "Duplicate plan file /foo/bar/project/recipe.js");
    c = assertConfig(
        new String[] {
            "--root=project",
            "project/recipe2.js", "/foo/bar/project/recipe.js",
            "project/recipe.js" },
        false,
        "Duplicate plan file /foo/bar/project/recipe.js");
  }

  public final void testMisspelledParams() throws IOException {
    assertConfig(
        new String[] { "-root=project" },
        false,
        "Unrecognized flag -root. Did you mean \"--root\"?",
        "Please specify --root");
  }

  private CommandLineConfig assertConfig(
      String[] argv, boolean ok, String... msgs) throws IOException {
    MessageQueue mq = new MessageQueue();
    FileSystem fs = new StubFileSystemProvider("mfs")
        .getFileSystem(URI.create("mfs://#/foo/bar"));
    fs.getPath("/foo/bar").createDirectory();
    fs.getPath("/foo/bar/project/src").createDirectory();
    fs.getPath("/foo/bar/tools").createDirectory();
    fs.getPath("/foo/bar/project/ptools").createDirectory();
    fs.getPath("/foo/bar/project/recipe.js").createFile();
    fs.getPath("/foo/bar/project/recipe2.js").createFile();
    fs.getPath("/foo/bar/project/src/main.cc").createFile();
    fs.getPath("/foo/bar/project/src/main.h").createFile();

    CommandLineConfig config = new CommandLineConfig(
        fs, mq, new CommandLineArgs(argv));
    assertEquals(Joiner.on('\n').join(msgs),
                 Joiner.on('\n').join(mq.getMessages()));
    assertEquals(ok, !mq.hasErrors());
    return config;
  }
}
