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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
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
      throws DependencyCycleException, MissingProductsException {
    // TODO: make sure all products are concrete, and back-propagate any
    // parameters to create the ones that are needed.
    {
      Set<BoundName> prodsNeeded = Sets.newHashSet(prods);
      prodsNeeded.removeAll(nodes.keySet());
      if (!prodsNeeded.isEmpty()) {
        throw new MissingProductsException(
            "Undefined products " + Joiner.on(", ").join(prodsNeeded));
      }
    }

    final Multimap<BoundName, BoundName> postReqGraph;
    {
      ImmutableMultimap.Builder<BoundName, BoundName> b
          = ImmutableMultimap.builder();
      for (Map.Entry<BoundName, BoundName> e : edges.entries()) {
        b.put(e.getValue(), e.getKey());
      }
      postReqGraph = b.build();
    }

    // Figure out all ingredients needed.
    final Set<BoundName> allProducts;
    {
      final ImmutableSet.Builder<BoundName> b = ImmutableSet.builder();
      Walker w = walker(new ProductNameAppender(b));
      for (BoundName prod : prods) { w.walk(prod); }
      allProducts = b.build();
    }

    // Now create a recipe from the subgraph, while keeping track of the
    // starting points.
    class IngredientProcesser {
      // Maps product names to the ingredient instance for that product.
      final Map<BoundName, Ingredient> ingredients = Maps.newHashMap();
      /**
       * The products we're processing so we can detect and report dependency
       * cycles.
       */
      final Set<BoundName> processing = Sets.newLinkedHashSet();

      Ingredient process(BoundName product) throws DependencyCycleException {
        Ingredient ingredient = ingredients.get(product);
        if (ingredient != null) { return ingredient; }
        if (processing.contains(product)) {
          List<BoundName> cycle = Lists.newArrayList(processing);
          int index = cycle.indexOf(product);
          cycle.subList(0, index).clear();
          cycle.add(product);
          throw new DependencyCycleException(
              "Cycle in product dependencies : " + Joiner.on(", ").join(cycle));
        }
        processing.add(product);
        ImmutableList.Builder<Ingredient> postReqs = ImmutableList.builder();
        for (BoundName postReq : postReqGraph.get(product)) {
          if (!allProducts.contains(postReq)) { continue; }
          Ingredient postReqIngredient = process(postReq);
          postReqs.add(postReqIngredient);
        }
        processing.remove(product);
        ingredient = new Ingredient(
            product, ImmutableList.copyOf(edges.get(product)),
            postReqs.build());
        ingredients.put(product, ingredient);
        return ingredient;
      }
    }
    IngredientProcesser w = new IngredientProcesser();
    // The products that have no prerequisites.  We start with these.
    final ImmutableList.Builder<Ingredient> startingPoints
        = ImmutableList.builder();
    for (BoundName prod : allProducts) {
      // Ingredient will not appear in any postrequisite lists if it has no
      // prerequisites.
      if (edges.get(prod).isEmpty()) {
        startingPoints.add(w.process(prod));
      }
    }
    if (!w.ingredients.keySet().containsAll(prods)) {
      // Flush out any products that would be missing due to dependency cycles
      // that cause the graph to be unreachable from a zero prerequisite
      // product.
      for (BoundName prod : allProducts) { w.process(prod); }
    }
    return new Recipe(startingPoints.build());
  }

  /** Thrown when a product is transitively its own prerequisite. */
  public static class DependencyCycleException extends Exception {
    public DependencyCycleException(String msg) { super(msg); }
  }

  public static class MissingProductsException extends Exception {
    public MissingProductsException(String msg) { super(msg); }
  }

  private static final class ProductNameAppender
      implements Function<BoundName, Void> {
    private final ImmutableSet.Builder<BoundName> b;
    ProductNameAppender(ImmutableSet.Builder<BoundName> b) { this.b = b; }
    public Void apply(BoundName productName) {
      b.add(productName);
      return null;
    }
  }
}
