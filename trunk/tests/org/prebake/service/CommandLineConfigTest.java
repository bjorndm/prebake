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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Map;
import java.util.regex.Pattern;

public class CommandLineConfigTest extends PbTestCase {
  private static final Map<String, String> PROPS = ImmutableMap.of(
      "java.io.tmpdir", "tmp",  // relative
      "java.class.path", "/jars/foo.jar",
      "java.library.path", "/lib");
  private static final Map<String, String> ENV = ImmutableMap.of(
      "PATH", "/bin");
  private static final String BOILERPLATE = (
      ""
      + "\"-Djava.class.path=/jars/foo.jar\","
      + "\"-Djava.library.path=/lib\","
      + "\"-Djava.io.tmpdir=/foo/bar/tmp\","  // absolute
      + "\"-Denv.path=/bin\"");

  @Test public final void testClientRoot() throws IOException {
    assertConfig(new String[0], false, "Please specify --root");
    Config c;
    c = assertConfig(
        new String[] { "--root=/" }, false,
        "Plan file /Bakefile.js is not a file");
    assertEquals(
        "[" + BOILERPLATE + ",\"--root\",\"/\",\"/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root", "/" }, false,
        "Plan file /Bakefile.js is not a file");
    assertEquals(
        "[" + BOILERPLATE + ",\"--root\",\"/\",\"/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root", "/foo" }, false,
        "Plan file /foo/Bakefile.js is not a file");
    assertEquals(
        "[" + BOILERPLATE + ",\"--root\",\"/foo\",\"/foo/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(new String[] { "--root", "/foo/bar/project" }, true);
    assertEquals(
        "[" + BOILERPLATE + ","
        + "\"--root\",\"/foo/bar/project\",\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/Bakefile.js" },
        true);
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
    assertEquals("[/foo/bar/project/Bakefile.js]", c.getPlanFiles().toString());
    assertEquals(
        "[" + BOILERPLATE + ","
        + "\"--root\",\"/foo/bar/project\",\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "project/Bakefilee.js" },
        false,
        "Plan file /foo/bar/project/Bakefilee.js is not a file");
    assertEquals(
        "[" + BOILERPLATE + ","
        + "\"--root\",\"/foo/bar/project\",\"/foo/bar/project/Bakefilee.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root", "/foo/bar/project", "--root=/foo" },
        false, "Dupe arg --root");
    assertEquals("/foo/bar/project", c.getClientRoot().toString());
    assertEquals(
        "[" + BOILERPLATE + ","
        + "\"--root\",\"/foo/bar/project\",\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
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
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"--ignore\",\"^.*~$\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
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
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(new String[] { "--root=project", "--umask=750" }, true);
    assertEquals(0750, c.getUmask());
    assertEquals(
        ""
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"--umask\",\"750\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
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

  @Test public final void testWwwPort() throws IOException {
    Config c;
    c = assertConfig(new String[] { "--root=project" }, true);
    assertEquals(-1, c.getWwwPort());
    assertEquals(
        ""
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root=project", "--www-port=8080" }, true);
    assertEquals(8080, c.getWwwPort());
    assertEquals(
        ""
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"--www-port\",\"8080\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    c = assertConfig(
        new String[] { "--root=project", "--www-port=8080", "--www-port=8000" },
        false, "Dupe arg --www-port");
    assertEquals(8080, c.getWwwPort());
    assertConfig(
        new String[] { "--root=project", "--www-port=abc" },
        false, "--www-port=abc is not a valid port");
    assertConfig(
        new String[] { "--root=project", "--www-port=0" },
        false, "--www-port=0 is not a valid port");
    assertConfig(new String[] { "--root=project", "--www-port=65535" }, true);
    assertConfig(
        new String[] { "--root=project", "--www-port=65536" },
        false, "--www-port=65536 is not a valid port");
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
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"--tools\",\"/foo/bar/tools:/foo/bar/project/ptools\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
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
    assertEquals("[/foo/bar/project/Bakefile.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] { "--root=project", "project/Bakefile.js" }, true);
    assertEquals("[/foo/bar/project/Bakefile.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] { "--root=project", "project/Bakefile2.js" }, true);
    assertEquals(
        "[/foo/bar/project/Bakefile2.js]", c.getPlanFiles().toString());
    c = assertConfig(
        new String[] {
            "--root=project", "project/Bakefile2.js", "project/Bakefile.js" },
        true);
    assertEquals(
        "[/foo/bar/project/Bakefile2.js, /foo/bar/project/Bakefile.js]",
        c.getPlanFiles().toString());
    assertEquals(
        ""
        + "[" + BOILERPLATE + ",\"--root\",\"/foo/bar/project\","
        + "\"/foo/bar/project/Bakefile2.js\","
        + "\"/foo/bar/project/Bakefile.js\"]",
        CommandLineConfig.toArgv(c, PROPS, ENV));
    assertConfig(
        new String[] {
            "--root=project",
            "project/Bakefile2.js", "project/Bakefile.js",
            "/foo/bar/project/Bakefile.js" },
        false,
        "Duplicate plan file /foo/bar/project/Bakefile.js");
    assertConfig(
        new String[] {
            "--root=project",
            "project/Bakefile2.js", "/foo/bar/project/Bakefile.js",
            "project/Bakefile.js" },
        false,
        "Duplicate plan file /foo/bar/project/Bakefile.js");
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
    FileSystem fs = fileSystemFromAsciiArt(
        "/foo/bar",
        "/",
        "  foo/",
        "    bar/",
        "      project/",
        "        Bakefile.js",
        "        Bakefile2.js",
        "        src/",
        "          main.cc",
        "          main.h",
        "        ptools/",
        "      tools/"
        );
    CommandLineConfig config = new CommandLineConfig(
        fs, mq, new CommandLineArgs(argv));
    assertEquals(Joiner.on('\n').join(msgs),
                 Joiner.on('\n').join(mq.getMessages()));
    assertEquals(ok, !mq.hasErrors());
    return config;
  }
}
