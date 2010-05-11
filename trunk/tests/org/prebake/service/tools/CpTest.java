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

public class CpTest extends ToolTestCase {
  public CpTest() { super("cp"); }

  @Test public final void testCpNothing() throws IOException {
    tester
        .expectLog("cp.js:##:INFO: Copied 0 files")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testCpFiles() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*.baz"))
        .withOutput(Glob.fromString("boo/*.far"))
        .withInputPath("foo/bar/x.baz", "foo/bar/y.baz")
        .expectExec(1, "cp", "foo/bar/x.baz", "boo/x.far")
        .expectLog("Running process 1")
        .expectExec(2, "cp", "foo/bar/y.baz", "boo/y.far")
        .expectLog("Running process 2")
        .expectLog("Waiting for process 1")
        .expectLog("Waiting for process 2")
        .expectLog("cp.js:##:INFO: Copied 2 files")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testCpBadXformer() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*.baz"))
        .withOutput(Glob.fromString("boo/*/*.far"))
        .withInputPath("foo/bar/x.baz", "foo/bar/y.baz")
        .expectLog(
            ""
            + "cp.js:##:SEVERE: Cannot map inputs to outputs :"
            + " Can't transform foo/bar/*.baz to boo/*/*.far."
            + "  There is no corresponding hole for boo/*")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testUnexpectedOption() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*.baz"))
        .withOutput(Glob.fromString("boo/*.far"))
        .withOption("no_such_option", "bogus")
        .withInputPath("foo/bar/x.baz")
        .expectExec(1, "cp", "foo/bar/x.baz", "boo/x.far")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("cp.js:##:WARNING: Unrecognized option no_such_option")
        .expectLog("cp.js:##:INFO: Copied 1 file")
        .expectLog("Exited with true")
        .run();
  }
}
