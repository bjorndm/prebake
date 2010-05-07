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

package org.prebake.service.bake;

import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WorkingFileCheckerTest extends PbTestCase {
  private WorkingFileChecker checker;
  private FileSystem fs;

  @Before public void setUp() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        "/",
        "  root/",
        "  tmp/",
        "    working/");
    checker = new WorkingFileChecker(
        fs.getPath("/root"), fs.getPath("/tmp/working"));
  }

  @After public void tearDown() throws IOException {
    fs.close();
    fs = null;
    checker = null;
  }

  @Test public final void testCheckPath() throws IOException {
    assertAllowed(fs.getPath("/tmp/working/foo"));
    assertAllowed(fs.getPath("perl"));
    assertAllowed(fs.getPath("/tmp/working/root"));
    assertAllowed(fs.getPath("/tmp/working/root/bar"));
    assertDisallowed(fs.getPath("/root"));
    assertDisallowed(fs.getPath("/root/foo"));
    assertDisallowed(fs.getPath("../../root/foo"));
  }

  @Test public final void testCheckString() {
    assertAllowed("");
    assertAllowed("foo");
    assertAllowed("bar");
    assertAllowed("/tmp/working");
    assertDisallowed("/root");
    assertDisallowed("../..//root");
    assertDisallowed("\\root");
    assertDisallowed("c:\\root");
    assertDisallowed("..\\..\\root");
    assertDisallowed("-I=/root/includes");
  }

  private void assertAllowed(Path p) throws IOException {
    assertEquals(p, checker.check(p));
  }
  private void assertDisallowed(Path p) {
    try {
      checker.check(p);
    } catch (IOException ex) {
      // OK
      return;
    }
    fail("Path " + p + " was allowed");
  }
  private void assertAllowed(String s) {
    assertEquals(s, checker.check(s));
  }
  private void assertDisallowed(String s) {
    try {
      checker.check(s);
    } catch (IllegalArgumentException ex) {
      // OK
      return;
    }
    fail("Command line part " + s + " was allowed");
  }
}
