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

package org.prebake.service.tools.ext;

import org.prebake.util.PbTestCase;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.FileSystem;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.internal.JUnitSystem;

/**
 * If writing test classes that run tests to test your test integration classes
 * is too meta for you, then stop reading.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class JUnitRunnerTest extends PbTestCase {
  @Test public final void testRunner() throws Exception {
    String testClass1 = FakeTestClass1.class.getName();
    String testClass2 = FakeTestClass2.class.getName();
    FileSystem fs = fileSystemFromAsciiArt(
        "/",
        (
         ""
         + "/\n"
         + "  test-report/"));
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    final PrintStream out = new PrintStream(outBytes, true /* auto-flushing */);
    int result = JUnitRunner.run(
        new JUnitSystem() {
          public void exit(int exitCode) { fail("Should not have exited"); }
          public PrintStream out() { return out; }
        },
        null, fs.getPath("/test-report"), Sets.newHashSet("json", "html"),
        testClass1, testClass2);
    String output = new String(outBytes.toByteArray(), Charsets.UTF_8);
    boolean ok = false;
    try {
      // Check the files created.  The contents of the HTML files are tested
      // in the tests for the HTML report generator.
      assertEquals(
          Joiner.on('\n').join(
              "/",
              "  test-report/",
              "    junit_tests.json \"...\"",
              "    index/",
              "      org.prebake.service.tools.ext/",
              "        JUnitRunnerTest$FakeTestClass1/",
              "          testError_0.html \"...\"",
              "          testFails_0.html \"...\"",
              "          testSucceeds_0.html \"...\"",
              "        JUnitRunnerTest$FakeTestClass1.html \"...\"",
              "        JUnitRunnerTest$FakeTestClass2/",
              "          testIgnored_0.html \"...\"",
              "          testSucceedsToo_0.html \"...\"",
              "        JUnitRunnerTest$FakeTestClass2.html \"...\"",
              "      org.prebake.service.tools.ext.html \"...\"",
              "    index.html \"...\"",
              "    junit_report.css \"...\"",
              ""),
          fileSystemToAsciiArt(fs, 40));

      // Test the content of the JSON test dump.
      assertEquals(
          Joiner.on("").join(
              "{",
                "\"tests\":[",
                  "{",
                    "\"class_name\":\"" + testClass1 + "\",",
                    "\"method_name\":\"testSucceeds\",",
                    "\"test_name\":\"testSucceeds(" + testClass1 + ")\",",
                    "\"annotations\":[",
                      "{",
                        "\"class_name\":\"org.junit.Test\",",
                        "\"text\":\"@org.junit.Test(",
                          "expected=class org.junit.Test$None, timeout=0)\"",
                      "}",
                    "],",
                    "\"out\":\"Hello, World!\\n\",",
                    "\"result\":\"success\"",
                  "},",
                  "{",
                    "\"class_name\":\"" + testClass1 + "\",",
                    "\"method_name\":\"testFails\",",
                    "\"test_name\":\"testFails(" + testClass1 + ")\",",
                    "\"annotations\":[",
                      "{",
                        "\"class_name\":\"org.junit.Test\",",
                        "\"text\":\"@org.junit.Test(",
                          "expected=class org.junit.Test$None, timeout=0)\"",
                      "}",
                    "],",
                    "\"failure_message\":\"\",",
                    "\"failure_trace\":\"java.lang.AssertionError: <elided>\",",
                    "\"out\":\"Maybe today is my lucky day!\\n\",",
                    "\"result\":\"failure\"",
                  "},",
                  "{",
                    "\"class_name\":\"" + testClass1 + "\",",
                    "\"method_name\":\"testError\",",
                    "\"test_name\":\"testError(" + testClass1 + ")\",",
                    "\"annotations\":[",
                      "{",
                        "\"class_name\":\"org.junit.Test\",",
                        "\"text\":\"@org.junit.Test(",
                          "expected=class org.junit.Test$None, timeout=0)\"",
                      "}",
                    "],",
                    "\"failure_message\":",
                      "\"For input string: \\\"eleventy\\\"\",",
                    "\"failure_trace\":\"java.lang.NumberFormatException:",
                      " For input string: \\\"eleventy\\\"<elided>\",",
                    "\"result\":\"error\"",
                  "},",
                  "{",
                    "\"class_name\":\"" + testClass2 + "\",",
                    "\"method_name\":\"testIgnored\",",
                    "\"test_name\":\"testIgnored(" + testClass2 + ")\",",
                    "\"annotations\":[",
                      "{",
                        "\"class_name\":\"org.junit.Ignore\",",
                        "\"text\":\"@org.junit.Ignore(value=)\"",
                      "},",
                      "{",
                        "\"class_name\":\"org.junit.Test\",",
                        "\"text\":\"@org.junit.Test(",
                          "expected=class org.junit.Test$None, timeout=0)\"",
                      "}",
                    "],",
                    "\"result\":\"ignored\"",
                  "},",
                  "{",
                    "\"class_name\":\"" + testClass2 + "\",",
                    "\"method_name\":\"testSucceedsToo\",",
                    "\"test_name\":\"testSucceedsToo(" + testClass2 + ")\",",
                    "\"annotations\":[",
                      "{",
                        "\"class_name\":\"org.junit.Test\",",
                        "\"text\":\"@org.junit.Test(",
                          "expected=class org.junit.Test$None, timeout=0)\"",
                      "}",
                    "],",
                    "\"result\":\"success\"",
                  "}",
                "],",
                "\"summary\":{",
                  "\"total\":5,",
                  "\"success\":2,",
                  "\"error\":1,",
                  "\"failure\":1,",
                  "\"ignored\":1",
                "}",
              "}"),
          CharStreams.toString(new InputStreamReader(
              fs.getPath("/test-report/junit_tests.json").newInputStream(),
              Charsets.UTF_8))
              .replaceAll("(?:\\\\n\\\\tat [^\\\\]*)+\\\\n", "<elided>"));

      assertEquals(-1, result);  // Some tests failed.

      assertTrue(
          output.endsWith(
              "error: 1, failure: 1, ignored: 1, success: 2, total: 5\n"));
      ok = true;
    } finally {
      if (!ok) { System.out.println(output); }
    }
  }

  // Test that 0 emitted on successful and ignored tests.
  // Test that -1 emitted on classes with no tests.
  // Test that -1 emitted on missing test classes.
  // Test that -2 emitted on failure to write reports.

  public static final class FakeTestClass1 {
    @Test public final void testSucceeds() {
      System.out.println("Hello, World!");
      assertTrue(true);
      assertFalse(false);
      assertNull(null);
    }

    @Test public final void testFails() {
      // The below is not quite epic.
      System.err.println("Maybe today is my lucky day!");
      assertTrue(false);  // Keep trying!  Better luck next time.
    }

    @Test public final void testError() {
      Integer.parseInt("eleventy", 10);
    }
  }

  public static final class FakeTestClass2 {
    @Ignore @Test public final void testIgnored() {
      System.out.println("Should not appear in output.");
      assertEquals("foo", "foo");
    }

    @Test public final void testSucceedsToo() {
      assertEquals(2, 1 + 1);
    }
  }
}
