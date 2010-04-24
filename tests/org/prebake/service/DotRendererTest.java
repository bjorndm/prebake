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

import org.prebake.service.plan.PlanGraph;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.util.Collections;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

public class DotRendererTest extends PbTestCase {
  @Test public final void testRender() throws IOException {
    PlanGraph g = PlanGraph.builder("foo", "bar", "baz")
        .edge("bar", "foo")
        .edge("baz", "bar")
        .edge("foo", "baz")
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, Collections.singleton("foo"), sb);
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
    PlanGraph g = PlanGraph.builder("foo", "bar", "baz")
        .edge("bar", "foo")
        .edge("baz", "bar")
        .edge("foo", "baz")
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of("foo", "baz"), sb);
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
    PlanGraph g = PlanGraph.builder("foo", "bar", "baz", "oink")
        .edge("bar", "foo")
        .edge("baz", "bar")
        .edge("foo", "baz")
        .edge("oink", "baz")
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of("oink", "baz"), sb);
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
    PlanGraph g = PlanGraph.builder("foo", "bar", "baz", "oink", "oinkoink")
        .edge("bar", "foo")
        .edge("baz", "bar")
        .edge("foo", "baz")
        .edge("oink", "oinkoink")
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of("baz"), sb);
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
    PlanGraph g = PlanGraph.builder("a", "b", "c")
        .edge("b", "a")
        .edge("b", "b")
        .build();
    StringBuilder sb = new StringBuilder();
    DotRenderer.render(g, ImmutableSet.of("a"), sb);
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
