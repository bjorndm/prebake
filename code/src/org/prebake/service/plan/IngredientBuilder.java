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

import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@ParametersAreNonnullByDefault
final class IngredientBuilder {
  final BoundName product;
  private final Set<BoundName> postReqs = Sets.newLinkedHashSet();
  private final ImmutableList.Builder<BoundName> preReqs
      = ImmutableList.builder();
  private Ingredient ingredient;
  private boolean building;

  IngredientBuilder(BoundName product) { this.product = product; }

  Ingredient build(Map<BoundName, IngredientBuilder> ingredients) {
    if (ingredient == null) {
      if (building) { throw new IllegalStateException(); }
      building = true;
      ImmutableList.Builder<Ingredient> postReqs = ImmutableList.builder();
      for (BoundName postReq : this.postReqs) {
        postReqs.add(ingredients.get(postReq).build(ingredients));
      }
      ingredient = new Ingredient(product, preReqs.build(), postReqs.build());
      building = false;
    }
    return ingredient;
  }

  void addPreRequisite(IngredientBuilder preReq) {
    if (preReq.postReqs.add(this.product)) {
      this.preReqs.add(preReq.product);
    }
  }
}
