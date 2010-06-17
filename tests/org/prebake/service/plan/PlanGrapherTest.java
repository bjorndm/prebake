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
}
