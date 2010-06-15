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

package org.prebake.service;

import org.prebake.core.BoundName;
import org.prebake.service.plan.PlanGraph;
import org.prebake.service.plan.PlanGraphTestCase;
import java.io.IOException;
import java.util.Collections;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

public class DotRendererTest extends PlanGraphTestCase {
  private static final BoundName FOO = BoundName.fromString("foo"),
     BAR = BoundName.fromString("bar"),
     BAZ = BoundName.fromString("baz"),
     OINK = BoundName.fromString("oink"),
     OINKOINK = BoundName.fromString("oinkoink"),
     A = BoundName.fromString("a"),
     B = BoundName.fromString("b"),
     C = BoundName.fromString("c");

  @Test public final void testRender() throws IOException {
    PlanGraph g = builder(FOO, BAR, BAZ)
        .edge(BAR, FOO)
        .edge(BAZ, BAR)
        .edge(FOO, BAZ)
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, Collections.singleton(FOO), sb);
    assertEquals(
        Joiner.on('\n').join(
            "digraph {",
            "  \"foo\";",
            "  \"bar\" -> \"foo\";",
            "  \"bar\";",
            "  \"baz\" -> \"bar\";",
            "  \"baz\";",
            "  \"foo\" -> \"baz\";",
            "}",
            ""),
        sb.toString());
  }

  @Test public final void testNonDisjointRoots1() throws IOException {
    PlanGraph g = builder(FOO, BAR, BAZ)
        .edge(BAR, FOO)
        .edge(BAZ, BAR)
        .edge(FOO, BAZ)
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of(FOO, BAZ), sb);
    assertEquals(
        Joiner.on('\n').join(
            "digraph {",
            "  \"foo\";",
            "  \"bar\" -> \"foo\";",
            "  \"bar\";",
            "  \"baz\" -> \"bar\";",
            "  \"baz\";",
            "  \"foo\" -> \"baz\";",
            "}",
            ""),
        sb.toString());
  }

  @Test public final void testNonDisjointRoots2() throws IOException {
    PlanGraph g = builder(FOO, BAR, BAZ, OINK)
        .edge(BAR, FOO)
        .edge(BAZ, BAR)
        .edge(FOO, BAZ)
        .edge(OINK, BAZ)
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of(OINK, BAZ), sb);
    assertEquals(
        Joiner.on('\n').join(
            "digraph {",
            "  \"baz\";",
            "  \"foo\" -> \"baz\";",
            "  \"oink\" -> \"baz\";",
            "  \"foo\";",
            "  \"bar\" -> \"foo\";",
            "  \"bar\";",
            "  \"baz\" -> \"bar\";",
            "  \"oink\";",
            "}",
            ""),
        sb.toString());
  }

  @Test public final void testPartialGraph() throws IOException {
    PlanGraph g = builder(FOO, BAR, BAZ, OINK, OINKOINK)
        .edge(BAR, FOO)
        .edge(BAZ, BAR)
        .edge(FOO, BAZ)
        .edge(OINK, OINKOINK)
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of(BAZ), sb);
    assertEquals(
        Joiner.on('\n').join(
            "digraph {",
            "  \"baz\";",
            "  \"foo\" -> \"baz\";",
            "  \"foo\";",
            "  \"bar\" -> \"foo\";",
            "  \"bar\";",
            "  \"baz\" -> \"bar\";",
            "}",
            ""),
        sb.toString());
  }

  @Test public final void testSelfEdge() throws IOException {
    PlanGraph g = builder(A, B, C)
        .edge(B, A)
        .edge(B, B)
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of(A), sb);
    assertEquals(
        Joiner.on('\n').join(
            "digraph {",
            "  \"a\";",
            "  \"b\" -> \"a\";",
            "  \"b\";",
            "  \"b\" -> \"b\";",
            "}",
            ""),
        sb.toString());
  }
}
