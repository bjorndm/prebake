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

public class FindbugsTest extends ToolTestCase {
  public FindbugsTest() { super("findbugs"); }

  @Test public final void testFindbugsHtmlOutput() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"), Glob.fromString("foo.jar"))
        .withOutput(Glob.fromString("reports/bugs.html"))
        .withInputPath("lib/Foo.class", "lib/Bar.class", "foo.jar")
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-html",
            "-output", "reports/bugs.html",
            "-auxclasspath", "foo.jar",
            "lib/Foo.class", "lib/Bar.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testFindbugsXmlOutput() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"), Glob.fromString("foo.jar"))
        .withOutput(Glob.fromString("reports/bugs.xml"))
        .withInputPath("lib/Foo.class", "lib/Bar.class", "foo.jar")
        .withOption("effort", "max")
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-effort:max", "-xml",
            "-output", "reports/bugs.xml",
            "-auxclasspath", "foo.jar",
            "lib/Foo.class", "lib/Bar.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testFindbugsXdocOutput() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"), Glob.fromString("foo.jar"))
        .withOutput(Glob.fromString("reports/bugs.xdoc"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .withOption("relaxed", true)
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-relaxed", "-xdocs",
            "-output", "reports/bugs.xdoc",
            "lib/Foo.class", "lib/Baz.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBadPriority() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"))
        .withOutput(Glob.fromString("bugs.html"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .withOption("priority", "higher")
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-html",
            "-output", "bugs.html",
            "lib/Foo.class", "lib/Baz.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("findbugs.js:##:WARNING: Bad priority higher")
        .expectLog("findbugs.js:##:INFO: Did you mean high?")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBadEffort() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"))
        .withOutput(Glob.fromString("bugs.html"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .withOption("effort", "minimum")
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-html",
            "-output", "bugs.html",
            "lib/Foo.class", "lib/Baz.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("findbugs.js:##:WARNING: Bad effort minimum")
        .expectLog("findbugs.js:##:INFO: Did you mean min?")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBadRelaxed() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"))
        .withOutput(Glob.fromString("bugs.html"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .withOption("relaxed", "yes")
        .expectExec(
            1, "findbugs", "-textui", "-progress", "-html",
            "-output", "bugs.html",
            "lib/Foo.class", "lib/Baz.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog(
            "findbugs.js:##:WARNING:"
            + " Option relaxed was not boolean, was \"yes\"")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoOutput() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .expectLog(
            "findbugs.js:##:SEVERE: No output file."
            + "  Please specify an output with an .html, .xml, or .xdoc"
            + " extension.")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testBadOutput() throws IOException {
    tester
        .withInput(Glob.fromString("lib/**.class"))
        .withOutput(Glob.fromString("*/bugs.html"))
        .withInputPath("lib/Foo.class", "lib/Baz.class")
        .expectLog(
            "findbugs.js:##:SEVERE: Cannot determine output file"
            + " : Can't transform foo to */bugs.html."
            + "  There is no corresponding hole for *")
        .expectLog("Exited with false")
        .run();
  }
}
