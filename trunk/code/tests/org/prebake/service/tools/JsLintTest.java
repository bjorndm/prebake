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

public class JsLintTest extends ToolTestCase {
  public JsLintTest() { super("jslint"); }

  @Test public final void testNoOptions() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.js"))
        .withOutput(Glob.fromString("out/reports/jslint///**"))
        .withInputPath("src/foo.js", "src/bar.js")
        .expectExec(
            1, "jslint", "--out", "out/reports/jslint",
            "--", "src/foo.js", "src/bar.js")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testBuiltins() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.js"))
        .withOutput(Glob.fromString("out/reports/jslint///**"))
        .withInputPath("src/foo.js", "src/bar.js")
        .withOption("builtin", ImmutableList.of("glob", "console"))
        .expectExec(
            1, "jslint", "--out", "out/reports/jslint",
            "--builtin", "glob", "--builtin", "console",
            "--", "src/foo.js", "src/bar.js")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testIgnores() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.js"))
        .withOutput(Glob.fromString("out/reports/jslint///**"))
        .withInputPath("src/foo.js", "src/bar.js")
        .withOption("ignore", ImmutableList.of("UNUSED_PROVIDE"))
        .expectExec(
            1, "jslint", "--out", "out/reports/jslint",
            "--ignore", "UNUSED_PROVIDE", "--", "src/foo.js", "src/bar.js")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoOutDir() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.js"))
        .withInputPath("src/foo.js", "src/bar.js")
        .expectLog(
            "jslint.js:##:SEVERE: Cannot determine output directory from []")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testNoInputs() throws IOException {
    tester
        .withInput(Glob.fromString("src///**.js"))
        .withOutput(Glob.fromString("out/reports/jslint///**"))
        .expectExec(1, "jslint", "--out", "out/reports/jslint", "--")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }
}
