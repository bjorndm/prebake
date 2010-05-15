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

import org.junit.Test;

public class JavadocTest extends ToolTestCase {
  public JavadocTest() { super("javadoc"); }

  @Test public final void testInferredOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src///com/foo/*.java"))
        .withOutput(Glob.fromString("docs///**.html"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .expectExec(
            1, "javadoc", "-d", "docs", "-quiet", "-sourcepath", "src",
            "src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testExplicitOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src///com/foo/*.java"))
        .withOutput(Glob.fromString("docs///**.html"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .withOption("d", "docs/java")
        .expectExec(
            1, "javadoc", "-d", "docs/java", "-quiet", "-sourcepath", "src",
            "src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testLinks() throws IOException {
    tester
        .withInput(Glob.fromString("src///com/foo/*.java"))
        .withOutput(Glob.fromString("docs///**.html"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .withOption("link", ImmutableList.of("docs/fooproj", "docs/barproj"))
        .expectExec(
            1, "javadoc", "-d", "docs", "-quiet",
            "-link", "docs/fooproj", "-link", "docs/barproj",
            "-sourcepath", "src",
            "src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testHeader() throws IOException {
    tester
        .withInput(Glob.fromString("src///com/foo/*.java"))
        .withOutput(Glob.fromString("docs///**.html"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .withOption("header", "<blink>Hello, World!</blink>")
        .expectExec(
            1, "javadoc", "-d", "docs", "-quiet", "-sourcepath", "src",
            "-header", "<blink>Hello, World!</blink>",
            "src/com/foo/Bar.java", "src/com/foo/Baz/java")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }
}
