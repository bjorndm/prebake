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

public class JsMinTest extends ToolTestCase {
  public JsMinTest() { super("jsmin"); }

  @Test public final void testMultipleOutputs() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.js"))
        .withOutput(Glob.fromString("out/**-min.js"))
        .withInputPath("src/foo.js", "src/bar.js")
        .expectExec(1, "jsmin", "--", "src/foo.js")
        .expectLog("Process 1 writing to out/foo-min.js")
        .expectExec(2, "jsmin", "--", "src/bar.js")
        .expectLog("Process 2 writing to out/bar-min.js")
        .expectLog("Running process 1")
        .expectLog("Running process 2")
        .expectLog("Waiting for process 1")
        .expectLog("Waiting for process 2")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testSingleOutput() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.js"))
        .withOutput(Glob.fromString("out/min.js"))
        .withInputPath("src/foo.js", "src/bar.js")
        .expectExec(1, "jsmin", "--", "src/foo.js", "src/bar.js")
        .expectLog("Process 1 writing to out/min.js")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNorename() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.js"))
        .withOutput(Glob.fromString("out/min.js"))
        .withInputPath("src/foo.js", "src/bar.js")
        .withOption("rename", false)
        .expectExec(1, "jsmin", "--norename", "--", "src/foo.js", "src/bar.js")
        .expectLog("Process 1 writing to out/min.js")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoInputs() throws IOException {
    tester
        .withInput(Glob.fromString("src/**.js"))
        .withOutput(Glob.fromString("out/**.js"))
        .expectLog("Exited with true")
        .run();
  }
}
