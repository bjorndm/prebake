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

public class RunTest extends ToolTestCase {
  public RunTest() { super("run"); }

  // This test depends on Baker generating an input list that is ordered by
  // the glob that matches the input.
  // That assertion is not tested here.

  @Test public final void testRun() throws IOException {
    tester
        .withInput(Glob.fromString("to-run.sh"),
                   Glob.fromString("file1"),
                   Glob.fromString("file2"))
        .withOutput(Glob.fromString("out/*"))
        .withInputPath("to-run.sh", "file1", "file2")
        .withOption(
            "args",
            ImmutableList.of("-x", ImmutableList.<String>of(), "-y"))
        .expectExec(1, "to-run.sh", "-x", "file1", "file2", "-y")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testUnknownOption() throws IOException {
    tester
        .withInput(Glob.fromString("to-run.sh"),
                   Glob.fromString("file1"),
                   Glob.fromString("file2"))
        .withOutput(Glob.fromString("out/*"))
        .withInputPath("to-run.sh", "file1", "file2")
        .withOption("no_such_option", "value")
        .expectLog(
            ""
            + "json-schema.js:##:SEVERE: Unknown property no_such_option"
            + " for run.action.options")
        .expectLog("json-schema.js:##:INFO: Did you mean args?")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testNoInputs() throws IOException {
    tester
       .withInput(Glob.fromString("to-run.sh"))
       .withOutput(Glob.fromString("out/*"))
       .expectLog("run.js:##:SEVERE: No inputs")
       .expectLog("Exited with false")
       .run();
  }
}
