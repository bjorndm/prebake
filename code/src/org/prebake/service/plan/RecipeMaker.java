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
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Makes a recipe from a plan graph.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see PlanGraph#makeRecipe
 */
@ParametersAreNonnullByDefault
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

    // Now create a recipe from the subgraph, while keeping track of the
    // starting points.
    class IngredientProcesser {
      // Maps product names to the ingredient instance for that product.
      final Map<BoundName, IngredientBuilder> ingredients = Maps.newHashMap();
      /**
       * The products we're processing so we can detect and report dependency
       * cycles.
       */
      final Set<BoundName> processing = Sets.newLinkedHashSet();
      /** The products that have no prerequisites.  We start with these. */
      final List<IngredientBuilder> startingPoints = Lists.newArrayList();
      IngredientBuilder process(BoundName prodName)
           throws DependencyCycleException, MissingProductException {
        IngredientBuilder ingredient = ingredients.get(prodName);
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
        ingredient = new IngredientBuilder(prodName);
        if (!processPreReqs(prod, ingredient)) {
          startingPoints.add(ingredient);
        }
        processing.remove(prodName);
        ingredients.put(prodName, ingredient);
        return ingredient;
      }

      private boolean processPreReqs(Product prod, IngredientBuilder ingredient)
          throws DependencyCycleException, MissingProductException {
        boolean hadPreReqs = false;
        for (BoundName preReqName : pg.edges.get(prod.name)) {
          hadPreReqs = true;
          Product preReq = nodes.get(preReqName);
          if (!preReq.isConcrete()) {
            Product derived = backPropagate(prod, preReq);
            Product existing = nodes.get(derived.name);
            if (existing == null) {
              preReq = derived;
              nodes.put(derived.name, derived);
            } else if (existing.isConcrete()) {
              // Template specialization, or the derived product has multiple
              // postRequisites in this recipe.
              preReq = existing;
            } else {
              throw new MissingProductException(
                  "Cannot derive concrete " + derived.name + " required by "
                  + prod.name + " because there is an existing abstract product"
                  + " with that name.");
            }
            preReqName = preReq.name;
          }
          ingredient.addPreRequisite(process(preReqName));
        }
        if (prod.template == null) { return hadPreReqs; }
        return hadPreReqs | processPreReqs(prod.template, ingredient);
      }
    }
    IngredientProcesser w = new IngredientProcesser();
    for (BoundName prod : prods) { w.process(prod); }
    ImmutableList.Builder<Ingredient> startingPoints = ImmutableList.builder();
    for (IngredientBuilder startingPoint : w.startingPoints) {
      startingPoints.add(startingPoint.build(w.ingredients));
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
                "Can't derive concrete version of " + preReqTemplate.name
                + " to satisfy " + postReq.name + " since " + inter
                + " matching " + input + " and " + output
                + " clashes with bindings " + bindings);
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
          + preReqTemplate.name + " to satisfy " + postReq.name + "."
          + "  Got " + bindings + ".");
    }
    return preReqTemplate.withParameterValues(bindings);
  }
}
