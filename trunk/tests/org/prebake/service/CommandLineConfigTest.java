package org.prebake.service;

import org.prebake.core.MessageQueue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.StubFileSystem;
import java.util.Arrays;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class CommandLineConfigTest extends TestCase {
  public final void testClientRoot() throws IOException {
    Config c;
    assertConfig(new String[0], false, "Please specify --root");
    assertConfig(
        new String[] { "--root=/" }, false,
        "Plan file /recipe.js is not a file");
    assertConfig(
        new String[] { "--root", "/" }, false,
        "Plan file /recipe.js is not a file");
    assertConfig(
        new String[] { "--root", "/foo" }, false,
        "Plan file /foo/recipe.js is not a file");
    assertConfig(new String[] { "--root", "/foo/bar/project" }, true);
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/recipe.js" },
        true);
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
    assertEquals("[/foo/bar/project/recipe.js]", c.getPlanFiles().toString());
    assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/recipee.js" },
        false,
        "Plan file /foo/bar/project/recipee.js is not a file");
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "--root=/foo" },
        false, "Dupe arg --root");
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
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
    c = assertConfig(
        new String[] { "--root=project", "--ignore=^.*~$", "--ignore=\\.bak$" },
        false, "Dupe arg --ignore");
    assertEquals("^.*~$", c.getIgnorePattern().pattern());
  }

  public final void testUmask() throws IOException {
    Config c;
    c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals(0640, c.getUmask());
    c = assertConfig(new String[] { "--root=project", "--umask=750" }, true);
    assertEquals(0750, c.getUmask());
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

  private CommandLineConfig assertConfig(
      String[] argv, boolean ok, String... msgs) throws IOException {
    MessageQueue mq = new MessageQueue();
    FileSystem fs = new StubFileSystem()
        .mkdir("/foo/bar/project/src")
        .mkdir("/foo/bar/tools")
        .mkdir("/foo/bar/project/ptools")
        .touch("/foo/bar/project/recipe.js")
        .touch("/foo/bar/project/recipe2.js")
        .touch("/foo/bar/project/src/main.cc")
        .touch("/foo/bar/project/src/main.h");
    CommandLineConfig config = new CommandLineConfig(fs, mq, argv);
    assertEquals(join("\n", msgs), join("\n", mq.getMessages()));
    assertEquals(ok, !mq.hasErrors());
    return config;
  }

  private static String join(String sep, String... els) {
    return join(sep, Arrays.asList(els));
  }

  private static String join(String sep, Iterable<?> els) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Object o : els) {
      if (first) {
        first = false;
      } else {
        sb.append(sep);
      }
      sb.append(o);
    }
    return sb.toString();
  }
}
