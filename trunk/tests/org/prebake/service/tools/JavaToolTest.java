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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;

public class JavaToolTest extends ToolTestCase {
  public JavaToolTest() { super("java"); }

  @Test public final void testOneJar() throws IOException {
    tester
        .withInput(Glob.fromString("jars/foo.jar"),
                   Glob.fromString("files/foo/**.txt"))
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption(
            "args",
            ImmutableList.of("-i", ImmutableList.of(), "-o", "out/bar.txt"))
        .withInputPath("jars/foo.jar", "files/foo/a.txt", "files/foo/b.txt")
        .expectExec(
            1, "java", "-jar", "jars/foo.jar",
            "-i", "files/foo/a.txt", "files/foo/b.txt", "-o", "out/bar.txt")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testExplicitClassName() throws IOException {
    tester
        .withInput(Glob.fromString("jars/foo.jar"))
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption("className", "com.example.Main")
        .withOption("args", ImmutableList.of("help"))
        .withInputPath("jars/foo.jar")
        .expectExec(
            1, "java", "-classpath", "jars/foo.jar", "com.example.Main", "help")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testMultipleJars() throws IOException {
    tester
        .withInput(Glob.fromString("jars/*.jar"))
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption("args", ImmutableList.of("Hello, World!"))
        // Since no className is specified, we assume jars/foo.jar has a
        // Class-Path in its manifest, which may include jars/bar.jar.
        .withInputPath("jars/foo.jar", "jars/bar.jar")
        .expectExec(1, "java", "-jar", "jars/foo.jar", "Hello, World!")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testMissingClassName() throws IOException {
    tester
        .withInput(Glob.fromString("classes///**.class"))
        .withOption("args", ImmutableList.of("Hello, World!"))
        .withInputPath("classes/com/example/Foo.class")
        .withInputPath("classes/com/example/Bar.class")
        .expectLog(
            "java.js:##:SEVERE: Missing class name.  Make sure the first"
            + " input is a jar with the Main-Class property in its manifest"
            + " or use the className option.")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testAsserts1() throws IOException {
    tester
        .withInput(Glob.fromString("jars/foo.jar"))
        .withOutput(Glob.fromString("out"))
        .withOption("className", "com.example.Main")
        .withOption("args", ImmutableList.of("out"))
        .withOption("asserts", true)
        .withInputPath("jars/foo.jar")
        .expectExec(
            1, "java", "-classpath", "jars/foo.jar", "-ea",
            "com.example.Main", "out")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testAsserts2() throws IOException {
    tester
        .withInput(Glob.fromString("jars/foo.jar"))
        .withOutput(Glob.fromString("out"))
        .withOption("className", "com.example.Main")
        .withOption("args", ImmutableList.of("out"))
        .withOption(
            "asserts",
            ImmutableList.of(
                "com.example", "-com.example.Main", "com.ancillary"))
        .withOption("systemAsserts", true)
        .withInputPath("jars/foo.jar")
        .expectExec(
            1, "java", "-classpath", "jars/foo.jar", "-ea:com.example",
            "-da:com.example.Main", "-ea:com.ancillary", "-esa",
            "com.example.Main", "out")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testSystemProperties() throws IOException {
    tester
        .withInput(Glob.fromString("jars/foo.jar"))
        .withOutput(Glob.fromString("out.txt"))
        .withOption("className", "com.example.Main")
        .withOption("args", ImmutableList.of("out.txt"))
        .withOption("systemProps", ImmutableMap.of("foo.bar", "baz", "a", "b"))
        .withInputPath("jars/foo.jar")
        .expectExec(
            1, "java", "-Dfoo.bar=baz", "-Da=b",
            "-classpath", "jars/foo.jar", "com.example.Main", "out.txt")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testVerbosity1() throws IOException {
    tester
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption("args", ImmutableList.of("x"))
        .withOption("verbose", true)
        .withInputPath("jars/foo.jar")
        .expectExec(1, "java", "-verbose", "-jar", "jars/foo.jar", "x")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testVerbosity2() throws IOException {
    tester
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption("args", ImmutableList.of("x"))
        .withOption("verbose", ImmutableList.of("class", "jni"))
        .withInputPath("jars/foo.jar")
        .expectExec(
            1, "java", "-verbose:class", "-verbose:jni",
            "-jar", "jars/foo.jar", "x")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testVm() throws IOException {
    tester
        .withOutput(Glob.fromString("out/bar.txt"))
        .withOption("args", ImmutableList.of("x"))
        .withOption("vm", "server")
        .withInputPath("jars/foo.jar")
        .expectExec(1, "java", "-server", "-jar", "jars/foo.jar", "x")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }
}
