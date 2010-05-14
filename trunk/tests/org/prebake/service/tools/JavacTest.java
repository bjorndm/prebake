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
            1, "javac", "-d", "lib", "-Xprefer:source", "-cp", "other-lib",
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
            1, "javac", "-d", "lib", "-Xprefer:source",
            "-cp", "jars/foo.jar:jars/bar.jar:other-lib",
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
        .withOption("cp", "jars/foo.jar:jars/bar.jar")
        .expectExec(
            1, "javac", "-d", "lib", "-Xprefer:source",
            "-cp", "jars/foo.jar:jars/bar.jar",
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
            1, "javac", "-d", "lib", "-Xprefer:source",
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
            1, "javac", "-d", "outDir", "-Xprefer:source",
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
        .expectLog(
            ""
            + "javac.js:##:SEVERE: Cannot determine output directory for class"
            + " files.  Please include a tree root in your output globs."
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
            1, "javac", "-d", "jartmp", "-Xprefer:source", "-g",
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
            1, "javac", "-d", "jartmp", "-Xprefer:source", "-g:vars",
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
        .expectExec(
            1, "javac", "-d", "jartmp", "-Xprefer:source",
            "src/com/foo/Bar.java", "src/com/foo/Boo.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog(
            ""
            + "javac.js:##:WARNING: Unrecognized value for flag \"g\":"
            + " \"columns\"")
        .expectLog("javac.js:##:INFO: Did you mean \"none\"?")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoWarn() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("nowarn", true)
        .expectExec(
            1, "javac", "-d", "jartmp", "-Xprefer:source", "-nowarn",
            "src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testSourceVersion() throws IOException {

  }

  @Test public final void testReleaseVersion() throws IOException {

  }

  @Test public final void testAnnotationProcessors() throws IOException {

  }

  @Test public final void testXlint() throws IOException {

  }

  @Test public final void testUnknownOption() throws IOException {

  }
}
