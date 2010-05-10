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
  @Test public final void testLs() throws IOException {
    new ToolTester("ls")
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
}
