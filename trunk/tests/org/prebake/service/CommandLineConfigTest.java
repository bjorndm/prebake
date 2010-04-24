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

import org.prebake.core.MessageQueue;
import org.prebake.util.CommandLineArgs;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Joiner;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.regex.Pattern;

public class CommandLineConfigTest extends PbTestCase {
  @Test public final void testClientRoot() throws IOException {
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

  @Test public final void testIgnorePattern() throws IOException {
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

  @Test public final void testUmask() throws IOException {
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

  @Test public final void testPathSeparator() throws IOException {
    Config c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals(":", c.getPathSeparator());
  }

  @Test public final void testToolPaths() throws IOException {
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

  @Test public final void testPlanPaths() throws IOException {
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
    assertConfig(
        new String[] {
            "--root=project",
            "project/recipe2.js", "project/recipe.js",
            "/foo/bar/project/recipe.js" },
        false,
        "Duplicate plan file /foo/bar/project/recipe.js");
    assertConfig(
        new String[] {
            "--root=project",
            "project/recipe2.js", "/foo/bar/project/recipe.js",
            "project/recipe.js" },
        false,
        "Duplicate plan file /foo/bar/project/recipe.js");
  }

  @Test public final void testMisspelledParams() throws IOException {
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
    mkdirs(fs.getPath("/foo/bar/project/src"));
    mkdirs(fs.getPath("/foo/bar/tools"));
    mkdirs(fs.getPath("/foo/bar/project/ptools"));
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
