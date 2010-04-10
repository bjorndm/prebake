package org.prebake.service.build;

import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.PlanGraph;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ParametersAreNonnullByDefault
public final class Baker {
  private final OperatingSystem os;

  public Baker(OperatingSystem os) { this.os = os; }

  public Recipe makeRecipe(final PlanGraph g, final Set<String> prods) {
    final List<String> roots = Lists.newArrayList(prods);
    // Eliminate any roots that are dependencies of others.
    // TODO: duplicative with code in DotRenderer
    {
      final Set<String> notRoots = Sets.newHashSet();
      for (final String prod : prods) {
        if (notRoots.contains(prod)) { break; }
        g.walker(new Function<String, Void>() {
          public Void apply(String node) {
            if (!node.equals(prod) && prods.contains(node)) {
              notRoots.add(node);
            }
            return null;
          }
        }).walk(prod);
      }
      roots.removeAll(notRoots);
    }
    final List<String> recipe = Lists.newArrayList();
    for (final String root : roots) {
      g.walker(new Function<String, Void>() {
        public Void apply(String node) {
          recipe.add(node);
          return null;
        }
      }).walk(root);
    }
    Collections.reverse(recipe);
    return new Recipe(ImmutableList.copyOf(recipe));
  }
}
