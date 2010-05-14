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

package org.prebake.service.tools;

import org.prebake.core.Glob;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

import org.junit.Test;

public class JavacTest extends ToolTestCase {
  public JavacTest() { super("javac"); }

  @Test public final void testInferredCp() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.java"),
                   Glob.fromString("tests///com/foo/*.java"),
                   Glob.fromString("other-lib///com/other/**.class"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath(
            "src/com/foo/Bar.java", "src/com/foo/Baz.java",
            "tests/com/foo/Boo.java")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "-classpath", "other-lib",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java",
            "tests/com/foo/Boo.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testInputJars() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.java"),
                   Glob.fromString("tests///com/foo/*.java"),
                   Glob.fromString("other-lib///com/other/**.class"),
                   Glob.fromString("jars/*.jar"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath(
            "src/com/foo/Bar.java", "src/com/foo/Baz.java",
            "tests/com/foo/Boo.java", "jars/foo.jar", "jars/bar.jar")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "-classpath", "jars/foo.jar:jars/bar.jar:other-lib",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java",
            "tests/com/foo/Boo.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }


  @Test public final void testExplicitCp() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.java"),
                   Glob.fromString("other-lib///com/other/**.class"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("classpath", "jars/foo.jar:jars/bar.jar")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "-classpath", "jars/foo.jar:jars/bar.jar",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testInferredOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.java"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testExplicitOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.java"))
        .withOutput(Glob.fromString("lib/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("d", "outDir")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "outDir",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.java"))
        .withOutput(Glob.fromString("lib/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectExec(
             1, "javac", "-Xprefer:source", "src/com/foo/Bar.java",
             "src/com/foo/Baz.java")
        .expectLog("Running process 1", "Waiting for process 1")
        .expectLog(
            ""
            + "javac.js:##:WARNING: Putting class files in same directory as"
            + " source files."
            + "  Maybe include a tree root in your output globs."
            + "  E.g., \"lib///**.class\"")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBadOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.java"))
        .withOutput(
            Glob.fromString("lib/**.class"),
            Glob.fromString("lib2///*.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog(
            ""
            + "javac.js:##:SEVERE: Cannot determine output directory for class"
            + " files."
            + "  Please include the same tree root in all your output globs."
            + "  E.g., \"lib///**.class\"")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testDebug() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("g", true)
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "jartmp", "-g",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testDebugWithValue() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("g", "vars")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "jartmp", "-g:vars",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testDebugWithBadValue() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Boo.java")
        .withOption("g", "columns")
        .expectLog(
            ""
            // TODO Maybe filter out stack frames with json-schema.js.
            + "json-schema.js:##:WARNING:"
            + " Illegal value \"columns\" for javac.action.options,g")
        .expectLog("json-schema.js:##:INFO: Did you mean \"none\"?")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testNoWarn() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("nowarn", true)
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "jartmp", "-nowarn",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testSourceVersion() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("source", "1.5")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "jartmp", "-source", "1.5",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBadSource() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("source", "1.5b")
        .expectLog(
            ""
            + "json-schema.js:##:SEVERE: Expected /^\\d+(?:\\.\\d+)?$/,"
            + " not \"1.5b\" for javac.action.options,source")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testTargetVersion() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("target", "1.5")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "jartmp", "-target", "1.5",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  // TODO: test annotation processor configuration

  @Test public final void testXlintRecommended() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("lib///com/foo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("Xlint", ImmutableList.of())
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib", "-Xlint",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testXlintListedOptions() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("lib///com/foo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("Xlint", ImmutableList.of("empty", "unchecked"))
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "-Xlint:empty,unchecked",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testXlintAll() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("lib///com/foo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("Xlint", "all")
        .expectExec(
            1, "javac", "-Xprefer:source", "-d", "lib",
            "-Xlint:all",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testUnknownOption() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("no_such_option", "value")
        .expectLog(
            ""
            + "json-schema.js:##:SEVERE: Unknown property no_such_option"
            + " for javac.action.options")
        .expectLog("json-schema.js:##:INFO: Did you mean nowarn?")
        .expectLog("Exited with false")
        .run();
  }
}
