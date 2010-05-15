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

public class JunitToolTest extends ToolTestCase {
  public JunitToolTest() { super("junit"); }

  @Test public final void testNoOptions() throws IOException {
    tester
        .withInput(Glob.fromString("lib///com/foo/*.class"))
        .withOutput(Glob.fromString("reports///**.html"))
        .withInputPath("lib/com/foo/Bar.class", "lib/com/foo/Baz.class")
        .expectExec(
            1, "java", "-classpath", "/java/jre.jar:/prebake.jar:lib",
            "org.prebake.service.tools.ext.JUnitRunner",
            "", "reports", "html",
            "lib/com/foo/Bar.class", "lib/com/foo/Baz.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  // TODO: flesh out tests

  @Override protected ImmutableMap<String, ?> stubToolHooks() {
    return ImmutableMap.of(
        "java_classpath", ImmutableList.of("/java/jre.jar", "/prebake.jar"));
  }
}
