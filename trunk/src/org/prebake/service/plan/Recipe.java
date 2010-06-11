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

import java.util.Collections;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

/**
 * A plan to build a set of {@link Product product}s by preparing some initial
 * {@link Ingredient ingredient}s, that then allows us to prepare more
 * complicated ingredients.
 *
 * <p>
 * We could represent a recipe as the post ordering of the spanning forest of
 * the {@link PlanGraph plan graph}.
 *
 * <p>
 * But we want to allow products to be built in parallel where there is no
 * overlap between their {@link Product#getInputs inputs} and
 * {@link Product#getOutputs outputs}.
 * So we represent a recipe as a list of {@link Ingredient ingredients} that
 * need to be made first, and each ingredient points to other ingredients that
 * need this ingredient.  Each ingredient has a count of ingredients that link
 * to it that need to be made first.
 *
 * <p>
 * Use the {@link Recipe#cook(Chef) cook} method to dispatch ingredients to
 * sous-chefs.  A sous-chef can report back by calling the {@code whenDone}
 * function.
 *
 * <p>
 * If all goes well, you will end up with a cake.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Recipe {
  public final ImmutableList<Ingredient> ingredients;

  public Recipe(ImmutableList<Ingredient> ingredients) {
    this.ingredients = ingredients;
  }

  /**
   * Dispatches ingredients to sous-chefs as their prerequisites are satisfied.
   */
  public void cook(Chef chef) {
    new Cooker(chef).cookIngredients(ingredients);
  }

  @ParametersAreNonnullByDefault
  public interface Chef {
    /**
     * Called with each ingredient as its prerequisites are prepared.
     * @param ingredient the one to make.
     * @param whenDone should be called by the chef when ingredient is prepared
     *     with the value {@code true} if it was successfully prepared, and
     *     {@code false} if not.
     */
    void cook(Ingredient ingredient, Function<Boolean, ?> whenDone);
    /**
     * Called when all sous-chefs have reported back.
     * @param allSucceeded if all sous-chef successfully prepared their
     *     ingredients.
     */
    void done(boolean allSucceeded);
  }
}

final class Cooker {
  private final Map<Ingredient, Integer> nIngredientsNeeded
      = Collections.synchronizedMap(
          Maps.<Ingredient, Integer>newIdentityHashMap());
  private final Recipe.Chef cook;
  Cooker(Recipe.Chef cook) { this.cook = cook; }
  private int outstanding = 0;
  private boolean failed;

  void cookIngredients(ImmutableList<Ingredient> ingredients) {
    synchronized (this) { outstanding += ingredients.size(); }
    for (final Ingredient ingredient : ingredients) {
      cook.cook(ingredient, new Function<Boolean, Void>() {
        public Void apply(Boolean success) {
          if (Boolean.TRUE.equals(success)) {
            final ImmutableList.Builder<Ingredient> readyToCook
                = ImmutableList.builder();
            synchronized (nIngredientsNeeded) {
              for (Ingredient postReq : ingredient.postRequisites) {
                Integer nNeededInt = nIngredientsNeeded.get(postReq);
                int nNeeded = nNeededInt != null
                    ? nNeededInt.intValue() : postReq.preRequisites.size();
                if (nNeeded == 1) {
                  nIngredientsNeeded.remove(postReq);
                  readyToCook.add(postReq);
                } else {
                  nIngredientsNeeded.put(postReq, nNeeded - 1);
                }
              }
            }
            cookIngredients(readyToCook.build());
          } else {
            failed = true;
          }
          synchronized (this) {
            if (--outstanding != 0) { return null; }
          }
          cook.done(!failed);
          return null;
        }
      });
    }
  }
}
