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

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;

/**
 * A directed graph where nodes are {@link Product}s and an edge exists between
 * to nodes where the source produces outputs that might be inputs to the
 * target.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class PlanGraph {
  public final ImmutableMap<BoundName, Product> nodes;
  /**
   * Contains the name of each product that the key product depends upon
   * non-transitively.
   */
  public final ImmutableMultimap<BoundName, BoundName> edges;

  private PlanGraph(
      ImmutableMap<BoundName, Product> nodes,
      ImmutableMultimap<BoundName, BoundName> edges) {
    this.nodes = nodes;
    this.edges = edges;
  }

  public static class Builder {
    private final ImmutableMap<BoundName, Product> nodes;
    private final ImmutableMultimap.Builder<BoundName, BoundName> edges
        = ImmutableMultimap.builder();

    private Builder(ImmutableMap<BoundName, Product> nodes) {
      this.nodes = nodes;
    }

    public Builder edge(BoundName prerequisite, BoundName postrequisite) {
      assert nodes.containsKey(postrequisite)
          && nodes.containsKey(prerequisite);
      edges.put(postrequisite, prerequisite);
      return this;
    }

    public PlanGraph build() {
      return new PlanGraph(nodes, edges.build());
    }
  }

  public static Builder builder(Product... nodes) {
    ImmutableMap.Builder<BoundName, Product> products = ImmutableMap.builder();
    for (Product p : nodes) { products.put(p.name, p); }
    return new Builder(products.build());
  }

  public Walker walker(Function<BoundName, ?> action) {
    return new Walker(this, action);
  }

  public static final class Walker {
    final PlanGraph g;
    final Function<BoundName, ?> action;
    final Set<BoundName> visited = Sets.newHashSet();

    private Walker(PlanGraph g, Function<BoundName, ?> action) {
      this.g = g;
      this.action = action;
    }

    public void walk(BoundName node) {
      if (!visited.add(node)) { return; }
      action.apply(node);
      for (BoundName desc : g.edges.get(node)) { walk(desc); }
    }
  }

  /**
   * @param prods the required products.
   */
  public Recipe makeRecipe(final Set<BoundName> prods)
      throws DependencyCycleException, MissingProductException {
    return new RecipeMaker(this).makeRecipe(prods);
  }
}
