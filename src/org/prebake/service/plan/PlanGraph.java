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
import org.prebake.core.Glob;
import org.prebake.core.GlobRelation;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.annotations.VisibleForTesting;
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
      throws DependencyCycleException, MissingProductException {
    return new RecipeMaker(this).makeRecipe(prods);
  }
}

final class RecipeMaker {
  final Map<BoundName, Product> nodes;
  final PlanGraph pg;

  RecipeMaker(PlanGraph pg) {
    this.nodes = Maps.newLinkedHashMap(pg.nodes);
    this.pg = pg;
  }
  /**
   * @param prods the required products.
   */
  public Recipe makeRecipe(final Set<BoundName> prods)
      throws DependencyCycleException, MissingProductException {
    {
      Set<BoundName> prodsNeeded = Sets.newLinkedHashSet();
      for (BoundName prod : prods) {
        Product p = nodes.get(prod);
        if (p != null) {
          if (p.isConcrete()) { continue; }
        } else if (!prod.bindings.isEmpty()) {
          BoundName baseName = BoundName.fromString(prod.getRawIdent());
          Product template = nodes.get(baseName);
          if (template != null && !template.isConcrete()) {
            nodes.put(prod, template.withParameterValues(prod.bindings));
            continue;
          }
          prodsNeeded.add(prod);
        }
      }
      if (!prodsNeeded.isEmpty()) {
        throw new MissingProductException(
            "Undefined products " + Joiner.on(", ").join(prodsNeeded));
      }
    }

    // Invert edges.
    final Multimap<BoundName, BoundName> postReqGraph;
    {
      ImmutableMultimap.Builder<BoundName, BoundName> b
          = ImmutableMultimap.builder();
      for (Map.Entry<BoundName, BoundName> e : pg.edges.entries()) {
        b.put(e.getValue(), e.getKey());
      }
      postReqGraph = b.build();
    }

    // Figure out all ingredients needed.
    final Set<BoundName> allProducts;
    {
      final ImmutableSet.Builder<BoundName> b = ImmutableSet.builder();
      PlanGraph.Walker w = pg.walker(new ProductNameAppender(b));
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

      Ingredient process(BoundName prodName)
           throws DependencyCycleException, MissingProductException {
        Ingredient ingredient = ingredients.get(prodName);
        // Reuse if already processed.
        if (ingredient != null) { return ingredient; }
        // Check cycles.
        if (processing.contains(prodName)) {
          List<BoundName> cycle = Lists.newArrayList(processing);
          int index = cycle.indexOf(prodName);
          cycle.subList(0, index).clear();
          cycle.add(prodName);
          throw new DependencyCycleException(
              "Cycle in product dependencies : " + Joiner.on(", ").join(cycle));
        }
        processing.add(prodName);
        Product prod = nodes.get(prodName);
        ImmutableList.Builder<Ingredient> postReqs = ImmutableList.builder();
        for (BoundName postReqName : postReqGraph.get(prodName)) {
          if (!allProducts.contains(postReqName)) { continue; }
          Product postReq = nodes.get(postReqName);
          if (!postReq.isConcrete()) {
            Product derived = backPropagate(prod, postReq);
            Product existing = nodes.get(derived.name);
            if (existing == null) {
              postReq = derived;
              nodes.put(derived.name, derived);
            } else if (existing.isConcrete()) {
              // Template specialization, or the derived product has multiple
              // postRequisites in this recipe.
              postReq = existing;
            } else {
              throw new MissingProductException(
                  "Cannot derive concrete " + derived.name + " required by "
                  + prodName + " because there is an existing abstract product"
                  + " with that name.");
            }
            postReqName = postReq.name;
          }
          Ingredient postReqIngredient = process(postReqName);
          postReqs.add(postReqIngredient);
        }
        processing.remove(prodName);
        ingredient = new Ingredient(
            prodName, ImmutableList.copyOf(pg.edges.get(prodName)),
            postReqs.build());
        ingredients.put(prodName, ingredient);
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
      if (pg.edges.get(prod).isEmpty()) {
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

  /**
   * Matches a postrequisite inputs against an abstract prerequisites outputs
   * to come up with a parameter set to derive a concrete parameterized
   * prerequiste.
   *
   * @param postReq concrete.
   * @param preReqTemplate abstract.
   * @return concrete product derived from preReqTemplate.
   */
  @VisibleForTesting
  static Product backPropagate(Product postReq, Product preReqTemplate)
      throws MissingProductException {
    Map<String, String> bindings = Maps.newLinkedHashMap(
        preReqTemplate.name.bindings);
    for (Glob input : postReq.filesAndParams.inputs) {
      for (Glob output : preReqTemplate.filesAndParams.outputs) {
        Glob inter = Glob.intersection(input, output);
        if (inter != null) {
          if (!output.match(inter.toString(), bindings)) {
            throw new MissingProductException(
                "Can't derive concrete version of " + preReqTemplate
                + " to satisfy " + postReq + " since " + inter + " matching "
                + input + " and " + output + " clashes with bindings "
                + bindings);
          }
        }
      }
    }
    // Make sure that the bindings contains all parameters so that we don't
    // create distinct products for foo["x":"y"] and foo["x":"y", "z":""]
    List<String> missingParams = null;
    for (GlobRelation.Param p
         : preReqTemplate.filesAndParams.parameters.values()) {
      if (!bindings.containsKey(p.name)) {
        if (p.defaultValue != null) {
          bindings.put(p.name, p.defaultValue);
        } else {
          if (missingParams == null) { missingParams = Lists.newArrayList(); }
          missingParams.add(p.name);
        }
      }
    }
    if (missingParams != null) {
      throw new MissingProductException(
          "Can't derive parameter" + (missingParams.size() == 1 ? " " : "s ")
          + missingParams + " for concrete version of "
          + preReqTemplate + " to satisfy " + postReq + ". Got " + bindings);
    }
    return preReqTemplate.withParameterValues(bindings);
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
