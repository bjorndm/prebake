package org.prebake.core;

import java.util.Arrays;

import junit.framework.TestCase;

public class GlobTest extends TestCase {
  public final void testEmptyGlob() {
    Glob emptyGlob = Glob.fromString("");
    assertEquals(Arrays.<String>asList(), emptyGlob.parts());
    assertEquals("", emptyGlob.toString());
    assertEquals(emptyGlob, Glob.fromString(""));
    try {
      emptyGlob.parts().add("foo");
      fail("added");
    } catch (UnsupportedOperationException ex) {
      // ok
    }
    assertEquals(Arrays.<String>asList(), emptyGlob.parts());
  }

  public final void testSimpleGlob() {
    Glob simpleGlob = Glob.fromString("foo");
    assertEquals(Arrays.asList("foo"), simpleGlob.parts());
    assertEquals("foo", simpleGlob.toString());
    assertEquals(simpleGlob, Glob.fromString("foo"));
    try {
      simpleGlob.parts().add("/");
      fail("added");
    } catch (UnsupportedOperationException ex) {
      // ok
    }
    assertEquals(
        Arrays.<String>asList("foo"), simpleGlob.parts());
  }

  public final void testFromString() {
    assertEquals(
        Arrays.asList("foo", "/", "bar.baz"),
        Glob.fromString("foo/bar.baz").parts());
    assertEquals(
        Arrays.asList("*", "/", "bar.baz"),
        Glob.fromString("*/bar.baz").parts());
    assertEquals(
        Arrays.asList("foo", "/", "**", "/", "bar.baz"),
        Glob.fromString("foo/**/bar.baz").parts());
    try {
      Glob.fromString("foo/***/bar.baz");
      fail("parsed");
    } catch (IllegalArgumentException ex) {
      // ok
    }
    try {
      Glob.fromString("foo**/bar.baz");
      fail("parsed");
    } catch (IllegalArgumentException ex) {
      // ok
    }
    try {
      Glob.fromString("foo//bar.baz");
      fail("parsed");
    } catch (IllegalArgumentException ex) {
      // ok
    }
  }

  public final void testIntersection() {
    assertEquals(
        Arrays.asList("*"),
        Glob.intersection(Glob.fromString("*"), Glob.fromString("*")).parts());
    assertEquals(
        Arrays.asList("**"),
        Glob.intersection(Glob.fromString("**"), Glob.fromString("**"))
        .parts());
    assertEquals(
        Arrays.asList("*"),
        Glob.intersection(Glob.fromString("*"), Glob.fromString("**")).parts());
    assertNull(
        Glob.intersection(Glob.fromString("foo"), Glob.fromString("bar")));
    assertEquals(
        Arrays.asList("foo"),
        Glob.intersection(Glob.fromString("foo"), Glob.fromString("foo"))
        .parts());
    assertEquals(
        Arrays.asList("foo", "/", "bar.baz"),
        Glob.intersection(Glob.fromString("foo/bar.baz"), Glob.fromString("**"))
        .parts());
    assertNull(
        Glob.intersection(Glob.fromString("foo/bar.baz"), Glob.fromString("*"))
        );
    assertEquals(
        Arrays.asList("bar.baz"),
        Glob.intersection(Glob.fromString("bar.baz"), Glob.fromString("*"))
        .parts());
    assertEquals(
        Arrays.asList("bar.baz"),
        Glob.intersection(Glob.fromString("bar.baz"), Glob.fromString("*.baz"))
        .parts());
    assertNull(
        Glob.intersection(Glob.fromString("bar.baz"), Glob.fromString("*.boo"))
        );
    assertEquals(
        Arrays.asList("bar.baz"),
        Glob.intersection(Glob.fromString("bar.baz"), Glob.fromString("**.baz"))
        .parts());
    assertNull(
        Glob.intersection(Glob.fromString("bar.baz"), Glob.fromString("**.boo"))
        );
    assertEquals(
        Arrays.asList("foo", "/", "*", "/", "*", "bar.baz"),
        Glob.intersection(
            Glob.fromString("foo/*/*.baz"), Glob.fromString("**bar.baz"))
            .parts());
    assertEquals(
        Arrays.asList(
            "src", "/", "com", "/", "google", "/", "caja", "/", "**",
            "/", "*", "Test.java"),
        Glob.intersection(
            Glob.fromString("src/**/*Test.java"),
            Glob.fromString("*/com/google/caja/**")).parts());
    assertEquals(
        Arrays.asList(
            "src", "/", "com", "/", "google", "/", "caja", "/", "**",
            "/", "*", "Test.java"),
        Glob.intersection(
            Glob.fromString("src/**/*Test.java"),
            Glob.fromString("*/com/google/caja/**.java"))
            .parts());
  }
}