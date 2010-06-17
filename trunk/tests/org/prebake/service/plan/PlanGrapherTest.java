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

package org.prebake.service.plan;

import org.prebake.core.BoundName;
import org.prebake.core.GlobRelation;
import org.prebake.core.GlobSet;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSource;
import org.prebake.service.plan.Recipe.Chef;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.Test;

public class PlanGrapherTest extends PlanGraphTestCase {

  @Test public final void testPlanGraph() {
    PlanGrapher grapher = new PlanGrapher();
    grapher.productListener.artifactChanged(
        product("foo", globs("*.a"), globs("*.b")));
    grapher.productListener.artifactChanged(
        product("bar", globs("*.b"), globs("*.c")));
    grapher.productListener.artifactChanged(
        product("baz", globs("*.c"), globs("*.d")));
    grapher.productListener.artifactChanged(
        product("boo", globs("*.c"), globs("*.e")));
    grapher.productListener.artifactChanged(
        product("far", globs("*.b", "*.d", "*.e"), globs("*.f")));

    {
      PlanGraph pg = grapher.snapshot();
      assertEquals("[bar, baz, boo, far, foo]", pg.nodes.keySet().toString());
      assertEquals(
          "{bar=[foo], baz=[bar], boo=[bar], far=[baz, boo, foo]}",
          pg.edges.toString());
    }

    grapher.productListener.artifactChanged(
        product("baz", globs("*.b"), globs("*.e")));
    grapher.productListener.artifactChanged(
        product("faz", globs("*.a"), globs("*.d")));

    {
      PlanGraph pg = grapher.snapshot();
      assertEquals(
          "[bar, baz, boo, far, faz, foo]", pg.nodes.keySet().toString());
      assertEquals(
          "{bar=[foo], baz=[foo], boo=[bar], far=[baz, boo, faz, foo]}",
          pg.edges.toString());
    }
  }

  private static final BoundName A = BoundName.fromString("A");
  private static final BoundName B = BoundName.fromString("B");
  private static final BoundName C = BoundName.fromString("C");
  private static final BoundName D = BoundName.fromString("D");
  private static final BoundName E = BoundName.fromString("E");
  private static final BoundName F = BoundName.fromString("F");
  private static final BoundName G = BoundName.fromString("G");
  private static final BoundName H = BoundName.fromString("H");
  private static final BoundName I = BoundName.fromString("I");

  @Test public final void testRecipe() throws Exception {
    //  A           E     G
    //    \       /
    //      C - D
    //    /       \
    //  B           F - H - I
    PlanGraph g = builder(A, B, C, D, E, F, G, H, I)
        .edge(A, C)
        .edge(B, C)
        .edge(C, D)
        .edge(D, E)
        .edge(D, F)
        .edge(F, H)
        .edge(H, I)
        .build();
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(E, F, C)), ImmutableSet.<BoundName>of(),
        "A", "B", "C", "D", "E", "F", "OK");
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(F, C)),
        ImmutableSet.<BoundName>of(),
        "A", "B", "C", "D", "F", "OK");
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(F, C, G)),
        ImmutableSet.<BoundName>of(),
        "A", "B", "C", "D", "F", "G", "OK");
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(G, F, C)),
        ImmutableSet.<BoundName>of(),
        "G", "A", "B", "C", "D", "F", "OK");
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(E, F)),
        ImmutableSet.of(D),
        "A", "B", "C", "D", "FAIL");
    assertCookLog(
        g.makeRecipe(ImmutableSet.of(E, H)),
        ImmutableSet.of(F),
        "A", "B", "C", "D", "E", "F", "FAIL");
  }

  @Test
  public final void testRecipeMakingLoopWithDependencyLeaf() throws Exception {
    PlanGraph g = builder(A, B, C, D)
        .edge(A, B)  // D - B = A
        .edge(B, A)  //       \
        .edge(B, C)  //         C
        .edge(D, B)
        .build();
    try {
      g.makeRecipe(ImmutableSet.of(C));
    } catch (DependencyCycleException ex) {
      assertEquals("Cycle in product dependencies : B, A, B", ex.getMessage());
      return;
    }
    fail("Loop");
  }

  @Test
  public final void testRecipeMakingLoopWithoutDependencyLeaf()
      throws Exception {
    PlanGraph g = builder(A, B, C)
        .edge(A, B)  // B = A
        .edge(B, A)  //   \
        .edge(B, C)  //     C
        .build();
    try {
      g.makeRecipe(ImmutableSet.of(C));
    } catch (DependencyCycleException ex) {
      assertEquals("Cycle in product dependencies : B, A, B", ex.getMessage());
      return;
    }
    fail("Loop");
  }

  @Test
  public final void testRecipeMakingConcurrent() throws Exception {
    //  A           F
    //    \       /
    //  B - D - E - G
    //    /       \
    //  C           H
    PlanGraph g = builder(A, B, C, D, E, F, G, H)
        .edge(A, D)
        .edge(B, D)
        .edge(C, D)
        .edge(D, E)
        .edge(E, F)
        .edge(E, G)
        .edge(E, H)
        .build();
    Random rnd = new Random();
    for (int run = 100; --run >= 0;) {
      Recipe r = g.makeRecipe(ImmutableSet.of(F, G, H));
      class Task {
        final BoundName prod;
        final Function<Boolean, ?> whenDone;
        Task(BoundName prod, Function<Boolean, ?> whenDone) {
          this.prod = prod;
          this.whenDone = whenDone;
        }
        @Override public String toString() { return prod.ident; }
      }
      final List<Task> tasks = Lists.newArrayList();
      final List<String> prods = Lists.newArrayList();
      r.cook(new Recipe.Chef() {
        public void cook(
            final Ingredient ingredient, final Function<Boolean, ?> whenDone) {
          tasks.add(new Task(ingredient.product, whenDone));
        }
        public void done(boolean allSucceeded) {
          prods.add(allSucceeded ? "OK" : "FAIL");
          tasks.add(null);
        }
      });
      while (true) {
        Task t = tasks.remove(rnd.nextInt(tasks.size()));
        if (t == null) { break; }
        prods.add(t.prod.ident);
        t.whenDone.apply(true);
      }
      assertTrue(Joiner.on('\n').join(tasks), tasks.isEmpty());
      prods.add("signalled");

      String prodsStr = prods.toString();
      assertEquals(prodsStr, 10, prods.size());
      assertTrue(prodsStr, prods.indexOf("A") < prods.indexOf("D"));
      assertTrue(prodsStr, prods.indexOf("B") < prods.indexOf("D"));
      assertTrue(prodsStr, prods.indexOf("C") < prods.indexOf("D"));
      assertTrue(prodsStr, prods.indexOf("D") < prods.indexOf("E"));
      assertTrue(prodsStr, prods.indexOf("E") < prods.indexOf("F"));
      assertTrue(prodsStr, prods.indexOf("E") < prods.indexOf("G"));
      assertTrue(prodsStr, prods.indexOf("E") < prods.indexOf("H"));
      assertEquals("OK", prods.get(8));
      assertEquals("signalled", prods.get(9));
    }
  }

  @Test public final void testAbstractInput() throws Exception {
    Product abstractP = parseProduct(
        "p",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['{src,gen}/**.c', 'headers/*(arch)/arch.h'],",
        "    'outputs': ['lib/*(arch)/**.o']",
        "  }],",
        "  'parameters': [{ 'name': 'arch' }]",
        "}");
    Product prereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'sporkc',",
        "    'inputs':  ['src/**.spork'],",
        "    'outputs': ['gen/**.c']",
        "  }]",
        "}");
    PlanGraph pg = PlanGraph.builder(abstractP, prereq)
        .edge(prereq.name, abstractP.name)
        .build();
    Recipe r = pg.makeRecipe(ImmutableSet.of(
        BoundName.fromString("p[\"arch\":\"x86\"]")));
    assertRecipe(
        r,
        "Ingredient : prereq",
        "  Enables  : p[\"arch\":\"x86\"]",
        "Ingredient : p[\"arch\":\"x86\"]",
        "  Requires : prereq",
        "SUCCESS");
  }

  @Test public final void testAbstractIntermediate() throws Exception {
    Product p = parseProduct(
        "p",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['obj/x86/**.o'],",
        "    'outputs': ['lib/x86.lib']",
        "  }]",
        "}");
    Product abstractPrereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': ['obj/*(arch)/**.o']",
        "  }],",
        "  'parameters': [{ 'name': 'arch' }]",
        "}");
    PlanGraph pg = PlanGraph.builder(p, abstractPrereq)
        .edge(abstractPrereq.name, p.name)
        .build();
    Recipe r = pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p")));
    assertRecipe(
        r,
        "Ingredient : prereq[\"arch\":\"x86\"]",
        "  Enables  : p",
        "Ingredient : p",
        "  Requires : prereq[\"arch\":\"x86\"]",
        "SUCCESS");
  }

  @Test
  public final void testAbstractIntermediateMissingParam() throws Exception {
    Product p = parseProduct(
        "p",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['obj/x86/**.o'],",
        "    'outputs': ['lib/x86.lib']",
        "  }]",
        "}");
    Product abstractPrereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': [",
        "      'obj/*(arch)/**.o',",
        //          *(bar) is not satisfied by any input in p
        "      'foo/*(bar)/**.baz'",
        "    ]",
        "  }],",
        "  'parameters': [{ 'name': 'arch' }, { 'name': 'bar' }]",
        "}");
    PlanGraph pg = PlanGraph.builder(p, abstractPrereq)
        .edge(abstractPrereq.name, p.name)
        .build();
    try {
      pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p")));
    } catch (MissingProductException ex) {
      assertEquals(
          ""
          + "Can't derive parameter [bar] for concrete version of prereq to"
          + " satisfy p.  Got {arch=x86}.",
          ex.getMessage());
      return;
    }
    fail("Expected MissingProductException");
  }

  @Test
  public final void testAbstractIntermediateDefaultParam() throws Exception {
    Product p = parseProduct(
        "p",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['obj/x86/**.o'],",
        "    'outputs': ['lib/x86.lib']",
        "  }]",
        "}");
    Product abstractPrereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': [",
        "      'obj/*(arch)/**.o',",
        //          *(bar) is not satisfied by any input in p
        "      'foo/*(bar)/**.baz'",
        "    ]",
        "  }],",
        "  'parameters': [",
        "    { 'name': 'arch' },",
        // But it is satisfied by a default value.
        "    { 'name': 'bar', 'default': 'BAR' }",
        "  ]",
        "}");
    PlanGraph pg = PlanGraph.builder(p, abstractPrereq)
        .edge(abstractPrereq.name, p.name)
        .build();
    Recipe r = pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p")));
    assertRecipe(
        r,
        "Ingredient : prereq[\"arch\":\"x86\",\"bar\":\"BAR\"]",
        "  Enables  : p",
        "Ingredient : p",
        "  Requires : prereq[\"arch\":\"x86\",\"bar\":\"BAR\"]",
        "SUCCESS");
  }

  @Test
  public final void testAbstractWithConflictingParameters() throws Exception {
    Product p = parseProduct(
        "p",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        // Require two different bindings for arch.
        "    'inputs':  ['obj/x86/**.o', 'obj/arm/**.o'],",
        "    'outputs': ['lib/x86.lib', 'lib/arm.lib']",
        "  }]",
        "}");
    Product abstractPrereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': ['obj/*(arch)/**.o']",
        "  }],",
        "  'parameters': [{ 'name': 'arch' }]",
        "}");
    PlanGraph pg = PlanGraph.builder(p, abstractPrereq)
        .edge(abstractPrereq.name, p.name)
        .build();
    try {
      pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p")));
    } catch (MissingProductException ex) {
      assertEquals(
          ""
          + "Can't derive concrete version of prereq to satisfy p since"
          + " obj/arm/**.o matching obj/arm/**.o and obj/*(arch)/**(arch).o"
          + " clashes with bindings {arch=x86}",
          ex.getMessage());
      return;
    }
    fail("Expected MissingProductException");
  }

  @Test public final void testTemplateSpecialization() throws Exception {
    Product p1 = parseProduct(
        "p1",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['obj/arm/**.o'],",
        "    'outputs': ['lib/arm.lib']",
        "  }]",
        "}");
    Product p2 = parseProduct(
        "p2",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['obj/x86/**.o'],",
        "    'outputs': ['lib/x86.lib']",
        "  }]",
        "}");
    Product abstractPrereq = parseProduct(
        "prereq",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': ['obj/*(arch)/**.o']",
        "  }],",
        "  'parameters': [{ 'name': 'arch' }]",
        "}");
    Product specializedPrereq = parseProduct(
        "prereq[\"arch\":\"arm\"]",
        "{",
        "  'actions': [{",
        "    'tool':    'gcc',",
        "    'inputs':  ['src/**.{c,h}'],",
        "    'outputs': ['obj/arm/**.o']",
        "  }]",
        "}");
    PlanGraph pg = PlanGraph.builder(p1, p2, abstractPrereq, specializedPrereq)
        .edge(abstractPrereq.name, p1.name)
        .edge(abstractPrereq.name, p2.name)
        .edge(specializedPrereq.name, p1.name)
        .build();
    Recipe r1 = pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p1")));
    assertRecipe(
        r1,
        "Ingredient : prereq[\"arch\":\"arm\"]",
        "  Enables  : p1",
        "Ingredient : p1",
        "  Requires : prereq[\"arch\":\"arm\"]",
        "SUCCESS");
    Recipe r2 = pg.makeRecipe(ImmutableSet.of(BoundName.fromString("p2")));
    assertRecipe(
        r2,
        "Ingredient : prereq[\"arch\":\"x86\"]",
        "  Enables  : p2",
        "Ingredient : p2",
        "  Requires : prereq[\"arch\":\"x86\"]",
        "SUCCESS");
  }

  private void assertRecipe(Recipe r, String... golden) {
    final List<String> log = Lists.newArrayList();
    r.cook(new Chef() {
      public void cook(Ingredient ingredient, Function<Boolean, ?> whenDone) {
        log.add("Ingredient : " + ingredient.product);
        for (BoundName preReqName : ingredient.preRequisites) {
          log.add("  Requires : " + preReqName);
        }
        for (Ingredient postReq : ingredient.postRequisites) {
          log.add("  Enables  : " + postReq.product);
        }
        whenDone.apply(true);
      }

      public void done(boolean allSucceeded) {
        log.add(allSucceeded ? "SUCCESS" : "FAILURE");
      }
    });
    assertEquals(Joiner.on('\n').join(golden), Joiner.on('\n').join(log));
  }

  private Product product(String name, GlobSet inputs, GlobSet outputs) {
    return new Product(
        BoundName.fromString(name), null,
        new GlobRelation(inputs, outputs),
        Collections.singletonList(new Action(
            "tool", inputs, outputs, ImmutableMap.<String, Object>of())),
        false, null, source);
  }

  private void assertCookLog(
      Recipe r, final Set<BoundName> burnt, String... golden) {
    final List<String> log = Lists.newArrayList();
    r.cook(new Recipe.Chef() {
      public void cook(Ingredient ingredient, Function<Boolean, ?> whenDone) {
        log.add(ingredient.product.ident);
        whenDone.apply(!burnt.contains(ingredient.product));
      }
      public void done(boolean allSucceeded) {
        log.add(allSucceeded ? "OK" : "FAIL");
      }
    });
    assertEquals(Joiner.on('\n').join(golden), Joiner.on('\n').join(log));
  }

  private Product parseProduct(String name, String... json) throws IOException {
    JsonSource src = new JsonSource(new StringReader(
        Joiner.on('\n').join(json).replace('\'', '"')));
    Object obj = src.nextValue();
    assertTrue(src.isEmpty());
    MessageQueue mq = new MessageQueue();
    Product p = Product.converter(BoundName.fromString(name), source)
        .convert(obj, mq);
    if (mq.hasErrors()) {
      for (String msg : mq.getMessages()) { System.err.println(msg); }
      fail();
    }
    return p;
  }
}
