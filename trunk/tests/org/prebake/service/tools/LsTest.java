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

public final class LsTest extends ToolTestCase {
  public LsTest() { super("ls"); }

  @Test public final void testLs() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*"))
        .withOutput(Glob.fromString("foo/barcontent"))
        .withInputPath("foo/bar/a")
        .withInputPath("foo/bar/b")
        .expectExec(1, "ls", "foo/bar/a", "foo/bar/b")
        .expectLog("Process 1 writing to foo/barcontent")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testIndeterminableOutputPath() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*"))
        .withOutput(Glob.fromString("foo/barcontent/*"))
        .withInputPath("foo/bar/a")
        .withInputPath("foo/bar/b")
        .expectLog(
            "ls.js:##:SEVERE: Bad output foo/barcontent/*.  Need full path")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testTooManyOutputs() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*"))
        .withOutput(Glob.fromString("foo/out1"), Glob.fromString("foo/out2"))
        .withInputPath("foo/bar/a")
        .withInputPath("foo/bar/b")
        .expectLog("ls.js:##:SEVERE: Too many outputs")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testTooFewOutputs() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*"))
        .withInputPath("foo/bar/a")
        .withInputPath("foo/bar/b")
        .expectExec(1, "ls", "foo/bar/a", "foo/bar/b")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testUnexpectedOption() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*.baz"))
        .withOutput(Glob.fromString("listing.txt"))
        .withOption("no_such_option", "bogus")
        .withInputPath("foo/bar/x.baz")
        .expectExec(1, "ls", "foo/bar/x.baz")
        .expectLog("Process 1 writing to listing.txt")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("ls.js:##:WARNING: Unrecognized option no_such_option")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testNoInputs() throws IOException {
    tester
        .withInput(Glob.fromString("foo/bar/*"))
        .expectExec(1, "echo")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }
}
