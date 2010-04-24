package org.prebake.service.plan;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
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
  public final ImmutableSet<String> nodes;
  /**
   * Contains the name of each product that the key product depends upon
   * non-transitively.
   */
  public final ImmutableMultimap<String, String> edges;

  private PlanGraph(
      ImmutableSet<String> nodes, ImmutableMultimap<String, String> edges) {
    this.nodes = nodes;
    this.edges = edges;
  }

  public static class Builder {
    private final ImmutableSet<String> nodes;
    private final ImmutableMultimap.Builder<String, String> edges
        = ImmutableMultimap.builder();

    private Builder(ImmutableSet<String> nodes) { this.nodes = nodes; }

    public Builder edge(String prerequisite, String node) {
      assert nodes.contains(node) && nodes.contains(prerequisite);
      edges.put(node, prerequisite);
      return this;
    }

    public PlanGraph build() {
      return new PlanGraph(nodes, edges.build());
    }
  }

  public static Builder builder(String... nodes) {
    return new Builder(ImmutableSet.of(nodes));
  }

  public Walker walker(Function<String, ?> action) {
    return new Walker(this, action);
  }

  public static final class Walker {
    final PlanGraph g;
    final Function<String, ?> action;
    final Set<String> visited = Sets.newHashSet();

    private Walker(PlanGraph g, Function<String, ?> action) {
      this.g = g;
      this.action = action;
    }

    public void walk(String node) {
      if (!visited.add(node)) { return; }
      action.apply(node);
      for (String desc : g.edges.get(node)) { walk(desc); }
    }
  }

  /**
   * @param prods
   */
  public Recipe makeRecipe(final Set<String> prods)
      throws DependencyCycleException, MissingProductsException {
    {
      Set<String> prodsNeeded = Sets.newHashSet(prods);
      prodsNeeded.removeAll(nodes);
      if (!prodsNeeded.isEmpty()) {
        throw new MissingProductsException(
            "Undefined products " + Joiner.on(", ").join(prodsNeeded));
      }
    }

    final Multimap<String, String> postReqGraph;
    {
      ImmutableMultimap.Builder<String, String> b = ImmutableMultimap.builder();
      for (Map.Entry<String, String> e : edges.entries()) {
        b.put(e.getValue(), e.getKey());
      }
      postReqGraph = b.build();
    }

    // Figure out all ingredients needed.
    final Set<String> allProducts;
    {
      final ImmutableSet.Builder<String> b = ImmutableSet.builder();
      Walker w = walker(new Function<String, Void>() {
        public Void apply(String productName) {
          b.add(productName);
          return null;
        }
      });
      for (String prod : prods) { w.walk(prod); }
      allProducts = b.build();
    }

    // Now create a recipe from the subgraph, while keeping track of the
    // starting points.
    class IngredientProcesser {
      // Maps product names to the ingredient instance for that product.
      final Map<String, Ingredient> ingredients = Maps.newHashMap();
      /**
       * The products we're processing so we can detect and report dependency
       * cycles.
       */
      final Set<String> processing = Sets.newLinkedHashSet();

      Ingredient process(String product) throws DependencyCycleException {
        Ingredient ingredient = ingredients.get(product);
        if (ingredient != null) { return ingredient; }
        if (processing.contains(product)) {
          List<String> cycle = Lists.newArrayList(processing);
          int index = cycle.indexOf(product);
          cycle.subList(0, index).clear();
          cycle.add(product);
          throw new DependencyCycleException(
              "Cycle in product dependencies : " + Joiner.on(", ").join(cycle));
        }
        processing.add(product);
        ImmutableList.Builder<Ingredient> postReqs = ImmutableList.builder();
        for (String postReq : postReqGraph.get(product)) {
          if (!allProducts.contains(postReq)) { continue; }
          Ingredient postReqIngredient = process(postReq);
          postReqs.add(postReqIngredient);
        }
        processing.remove(product);
        ingredient = new Ingredient(
            product, edges.get(product).size(), postReqs.build());
        ingredients.put(product, ingredient);
        return ingredient;
      }
    }
    IngredientProcesser w = new IngredientProcesser();
    // The products that have no prerequisites.  We start with these.
    final ImmutableList.Builder<Ingredient> startingPoints
        = ImmutableList.builder();
    for (String prod : allProducts) {
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
      for (String prod : allProducts) { w.process(prod); }
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
}
