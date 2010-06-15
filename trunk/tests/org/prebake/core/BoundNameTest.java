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

package org.prebake.core;

import org.prebake.util.PbTestCase;

import org.junit.Test;

public class BoundNameTest extends PbTestCase {
  @Test public final void testSimpleIdent() {
    assertEquals("foo", BoundName.fromString("foo").ident);
    assertEquals("bar", BoundName.fromString("bar").ident);
    assertEquals("BAZ", BoundName.fromString("BAZ").ident);
    assertEquals("h1", BoundName.fromString("h1").ident);
    assertEquals("one.two.thr33", BoundName.fromString("one.two.thr33").ident);
    // Normalized
    assertEquals("foo", BoundName.fromString("foo[]").ident);
  }

  @Test public final void testSingleBinding() {
    assertEquals(
        "foo[\"x\":\"y\"]",
        BoundName.fromString("foo[\"x\":\"y\"]").ident);
    assertEquals(
        "foo[\"a\":\"\\\\\"]",
        BoundName.fromString("foo[\"\\u0061\":\"\\\\\"]").ident);
  }

  @Test public final void testMultipleBinding() {
    assertEquals(
        "foo[\"a\":\"b\",\"c\":\"d\"]",
        BoundName.fromString("foo[\"a\":\"b\",\"c\":\"d\"]").ident);
    // Order is canonicalized
    assertEquals(
        "foo[\"a\":\"b\",\"c\":\"d\"]",
        BoundName.fromString("foo[\"c\":\"d\",\"a\":\"b\"]").ident);
    // Dupes dropped
    assertEquals(
        "foo[\"a\":\"b\",\"c\":\"d\"]",
        BoundName.fromString("foo[\"c\":\"d\",\"a\":\"b\",\"c\":\"d\"]").ident);
  }

  @Test public final void testMalformed() {
    assertIsBadBoundName("");
    assertIsBadBoundName("1");
    assertIsBadBoundName("a.");
    assertIsBadBoundName(".a");
    assertIsBadBoundName("a[");
    assertIsBadBoundName("a]");
    assertIsBadBoundName("[]");
    assertIsBadBoundName("[\"x\":\"y\"]");
    assertIsBadBoundName("a[\"x\"\"y\"]");
    assertIsBadBoundName("a[\"x\",\"y\"]");
    assertIsBadBoundName("a[\"x\":]");
    assertIsBadBoundName("a[\"x\":\"y\":\"p\":\"q\"]");
    assertIsBadBoundName("a[\"x\":\"y\",\"x\":\"z\"]");
  }

  private static void assertIsBadBoundName(String s) {
    try {
      BoundName.fromString(s);
    } catch (IllegalArgumentException ex) {
      return;  // OK
    }
    fail(s);
  }
}
