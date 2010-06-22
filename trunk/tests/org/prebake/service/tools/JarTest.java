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
import org.mozilla.javascript.JavaScriptException;

public class JarTest extends ToolTestCase {
  public JarTest() { super("jar"); }

  @Test public final void testInferredCreate() throws IOException {
    tester
        .withInput(Glob.fromString("lib///**.class"))
        .withOutput(Glob.fromString("foo.jar"))
        .withInputPath("lib/x/A.class", "lib/y/B.class")
        .expectExec(
            1, "$$jar", "c", "foo.jar",
            "-1", "lib", "2", "x/A.class", "y/B.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testInferredExtract() throws IOException {
    tester
        .withInput(Glob.fromString("foo.jar"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath("foo.jar")
        .expectExec(1, "$$jar", "x", "foo.jar", "lib///**.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testCannotInferAction() throws IOException {
    try {
      tester
          .withInput(Glob.fromString("foo.bar"))
          .withOutput(Glob.fromString("lib///**.class"))
          .withInputPath("foo.bar")
          .run();
    } catch (JavaScriptException ex) {
      assertEquals(
          "Error: Expected a fully specified archive, not lib///**.class.  If"
          + " you are trying to extract, specify the option { operation: 'x' }."
          + " (/--baked-in--/tools/jar.js#?)",
          ex.getMessage().replaceAll("\\.js#\\d+", ".js#?"));
      return;
    }
    fail("Ran successfully");
  }

  @Test public final void testExplicitAtion() throws IOException {
    tester
        .withInput(Glob.fromString("foo.jar"))
        .withOutput(Glob.fromString("lib///**.class"))
        .withInputPath("foo.jar")
        .withOption("operation", "x")
        .expectExec(1, "$$jar", "x", "foo.jar", "lib///**.class")
        .expectLog("Running process 1")
        .expectLog("Waiting for process 1")
        .expectLog("Exited with true")
        .run();
  }

  @Test public final void testUnknownOption() throws IOException {
    tester
        .withInput(Glob.fromString("src/com/foo/**.java"))
        .withOutput(Glob.fromString("jartmp///com/goo/**.class"))
        .withInputPath("src/com/foo/Bar.java", "src/com/foo/Baz.java")
        .withOption("no_such_option", "value")
        .expectLog(
            ""
            + "json-schema.js:##:SEVERE: Unknown property no_such_option"
            + " for jar.action.options")
        .expectLog("json-schema.js:##:INFO: Did you mean operation?")
        .expectLog("Exited with false")
        .run();
  }

  @Test public final void testNoInputs() throws IOException {
    tester
       .withOutput(Glob.fromString("foo.jar"))
       .expectExec(1, "$$jar", "c", "foo.jar", "-1")
       .expectLog("Running process 1")
       .expectLog("Waiting for process 1")
       .expectLog("Exited with true")
       .run();
  }
}
