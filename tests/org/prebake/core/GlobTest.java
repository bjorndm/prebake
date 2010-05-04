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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.Test;

public class GlobTest extends PbTestCase {
  @Test public final void testEmptyGlob() {
    Glob emptyGlob = Glob.fromString("");
    assertEquals(Arrays.<String>asList(), emptyGlob.parts());
    assertEquals("", emptyGlob.toString());
    assertEquals(emptyGlob, Glob.fromString(""));
    try {
      emptyGlob.parts().add("foo");
      fail("added");
    } catch (UnsupportedOperationException ex) {
      // OK
    }
    assertEquals(Arrays.<String>asList(), emptyGlob.parts());
  }

  @Test public final void testSimpleGlob() {
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

  @Test public final void testFromString() {
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
    } catch (Glob.GlobSyntaxException ex) {
      // ok
    }
    try {
      Glob.fromString("foo**/bar.baz");
      fail("parsed");
    } catch (Glob.GlobSyntaxException ex) {
      // ok
    }
    try {
      Glob.fromString("foo//bar.baz");
      fail("parsed");
    } catch (Glob.GlobSyntaxException ex) {
      // ok
    }
  }

  @Test public final void testTreeRoot() {
    assertEquals("", Glob.fromString("foo").getTreeRoot());
    assertEquals("", Glob.fromString("foo/bar").getTreeRoot());
    assertEquals("", Glob.fromString("foo/*").getTreeRoot());
    assertEquals("", Glob.fromString("foo/**/*.bar").getTreeRoot());
    assertEquals(
        "foo", Glob.fromString("foo///**/*.bar").getTreeRoot());
    assertEquals(
        "foo/bar", Glob.fromString("foo/bar///**/*.bar").getTreeRoot());
    do {
      try {
        Glob.fromString("foo/**///*.bar");
      } catch (Glob.GlobSyntaxException ex) {
        break;  // ok
      }
      fail("tree root cannot contain wildcard");
    } while (false);
    do {
      try {
        Glob.fromString("foo/bar///baz///*.bar");
      } catch (Glob.GlobSyntaxException ex) {
        break;  // ok
      }
      fail("tree root cannot have two tree root markers");
    } while (false);
    assertEquals(
        "foo/bar///**/*.bar", "" + Glob.fromString("foo/bar///**/*.bar"));
    assertEquals(
        "\\Qfoo\\E[/\\\\]\\Qbar\\E[/\\\\](?:.+[/\\\\])?[^/\\\\]*\\Q.bar\\E",
        Glob.toRegex(ImmutableList.of(Glob.fromString("foo/bar///**/*.bar")))
            .pattern());
  }

  @Test public final void testIntersection() {
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

  @Test public final void testConverter() {
    MessageQueue mq = new MessageQueue();
    assertEquals("[**/*.foo]", "" + Glob.CONV.convert("**/*.foo", mq));
    assertTrue(mq.getMessages().isEmpty());
    assertEquals("[**/*.{foo}]", "" + Glob.CONV.convert("**/*.{foo}", mq));
    assertTrue(mq.getMessages().isEmpty());
    assertEquals(
        "[**/*.foo, **/*.bar]", "" + Glob.CONV.convert("**/*.{foo,bar}", mq));
    assertTrue(mq.getMessages().isEmpty());
    assertEquals(
        "[a0, a1, a, b0, b1, b, c0, c1, c]",
        "" + Glob.CONV.convert("{a,b,c}{0,1,}", mq));
    assertTrue(mq.getMessages().isEmpty());
    assertEquals(
        "[foo/*.x, foo/*.y, foo/bar]",
        "" + Glob.CONV.convert("foo/{*.x,*.y,bar}", mq));
    assertTrue(mq.getMessages().isEmpty());
    assertEquals("[foo, foo/bar]", "" + Glob.CONV.convert("foo/{/,bar}", mq));
  }

  @Test public final void testCommonPrefix() {
    assertEquals(
        "/foo/",
        Glob.commonPrefix(Lists.newArrayList(Glob.fromString("/foo/"))));
    assertEquals(
        "/foo/",
        Glob.commonPrefix(Lists.newArrayList(Glob.fromString("/foo/*"))));
    assertEquals(
        "/foo/",
        Glob.commonPrefix(Lists.newArrayList(
            Glob.fromString("/foo/*"),
            Glob.fromString("/foo/bar"))));
    assertEquals(
        "/foo/",
        Glob.commonPrefix(Lists.newArrayList(
            Glob.fromString("/foo/bar"),
            Glob.fromString("/foo/*"))));
  }

  @Test public final void testToRegex() {
    assertEquals(
        "\\Qsrc\\E[/\\\\](?:.+[/\\\\])?[^/\\\\]*\\Q.c\\E",
        Glob.toRegex(Collections.singleton(Glob.fromString("src/**/*.c")))
            .pattern());
    assertRegexMatches(Arrays.<String>asList());
    assertRegexMatches(Arrays.<String>asList("/foo"), "/foo");
    assertRegexMatches(Arrays.<String>asList("/foo/"), "/foo");
    assertRegexMatches(Arrays.<String>asList("/foo/*"), "/foo", "/foo/bar");
    assertRegexMatches(
        Arrays.<String>asList("/foo/**"),
        "/foo", "/foo/bar", "/foo/bar/baz.txt");
    assertRegexMatches(
        Arrays.<String>asList("/foo/**.txt"),
        "/foo/bar/baz.txt");
  }

  @Test public final void testGetPathContainingAllMatches() throws IOException {
    Path base = this.fileSystemFromAsciiArt(
        "/foo",
        Joiner.on('\n').join(
            "/",
            "  foo/",
            "    bar/")).getPath("/foo");
    assertEquals(
        "/foo/bar",
        "" + Glob.fromString("bar/*.baz").getPathContainingAllMatches(base));
    assertEquals(
        "/foo/bar",
        "" + Glob.fromString("bar/**.baz").getPathContainingAllMatches(base));
    assertEquals(
        "/foo/bar",
        "" + Glob.fromString("bar/baz.boo").getPathContainingAllMatches(base));
    assertEquals(
        "/foo/bar/baz.boo",
        "" + Glob.fromString("bar/baz.boo/").getPathContainingAllMatches(base));
    assertEquals(
        "/",
        "" + Glob.fromString("/").getPathContainingAllMatches(base));
    assertEquals(
        "/boo",
        "" + Glob.fromString("/boo/").getPathContainingAllMatches(base));
    assertEquals(
        "/",
        "" + Glob.fromString("/boo").getPathContainingAllMatches(base));
    assertEquals(
        "/bar",
        "" + Glob.fromString("/bar/*.baz").getPathContainingAllMatches(base));
  }

  @Test(expected=IllegalArgumentException.class)
  public final void testTransformMissingHole() {
    Glob.transform(Glob.fromString("*.foo"), Glob.fromString("*/*.bar"));
  }

  @Test(expected=IllegalArgumentException.class)
  public final void testTransformIncompatibleHole() {
    Glob.transform(Glob.fromString("**/*.foo"), Glob.fromString("*/*.bar"));
  }

  @Test public final void testTransform() {
    assertTransform(
        "lib/foo/bar.o", "src/**/*.c", "lib/**/*.o", "src/foo/bar.c");
    assertTransform(
        "lib/bar.o", "src/**/*.c", "lib/**/*.o", "src/bar.c");
    assertTransform(
        null, "src/**/*.c", "lib/**/*.o", "src/bar.cc");
    assertTransform("bar/x.txt", "**/foo/*.txt", "bar/*.txt", "a/foo/x.txt");
    assertTransform("bar/y.txt", "**/foo/*.txt", "bar/*.txt", "b/c/foo/y.txt");
    assertTransform("bar/y.txt", "**/foo/*.txt", "**/bar/*.txt", "foo/y.txt");
    assertTransform(
        "baz/bar/y.txt", "**/foo/*.txt", "**/bar/*.txt", "baz/foo/y.txt");
    assertTransform("bar/a.txt", "foo/**/*.txt", "bar/**/*.txt", "foo/a.txt");
    assertTransform("foo/bar.txt", "lib/**", "**", "lib/foo/bar.txt");
    assertTransform("/foo/foo/bar.txt", "lib/**", "/foo/**", "lib/foo/bar.txt");
  }

  @Test public final void testNormGlob() {
    assertEquals("", Glob.normGlob(""));
    assertEquals("/", Glob.normGlob("/"));
    assertEquals("/foo", Glob.normGlob("/foo"));
    assertEquals("/foo", Glob.normGlob("/foo/"));
    assertEquals("/foo/bar", Glob.normGlob("/foo//bar/"));
    assertEquals("/foo/bar/baz", Glob.normGlob("/foo//bar//baz"));
    assertEquals("lib///org/prebake", Glob.normGlob("lib///org/prebake"));
    assertEquals("lib///org/prebake", Glob.normGlob("lib///org/prebake/"));
    assertEquals("lib///org/prebake", Glob.normGlob("lib///org//prebake/"));
    assertEquals("lib/org/prebake", Glob.normGlob("lib//org//prebake/"));
  }

  private void assertRegexMatches(List<String> globStrs, String... golden) {
    List<Glob> globs = Lists.newArrayList();
    for (String globStr : globStrs) { globs.add(Glob.fromString(globStr)); }
    Pattern p = Glob.toRegex(globs);
    {
      List<String> actual = Lists.newArrayList();
      for (String candidate : new String[] {
            "/",
            "/foo",
            "/foo/bar",
            "/foo/bar/baz.txt",
           }) {
        if (p.matcher(candidate).matches()) { actual.add(candidate); }
      }
      assertEquals(
          Joiner.on(" ; ").join(golden),
          Joiner.on(" ; ").join(actual));
    }
    {
      List<String> actual = Lists.newArrayList();
      for (String candidate : new String[] {
            "\\",
            "\\foo",
            "\\foo\\bar",
            "\\foo\\bar\\baz.txt",
           }) {
        if (p.matcher(candidate).matches()) { actual.add(candidate); }
      }
      assertEquals(
          Joiner.on(" ; ").join(golden).replace('/', '\\'),
          Joiner.on(" ; ").join(actual));
    }
  }

  private void assertTransform(
      @Nullable String golden, String input, String output, String path) {
    Function<String, String> xform = Glob.transform(
        Glob.fromString(input), Glob.fromString(output));
    assertEquals(golden, xform.apply(path));
  }
}
