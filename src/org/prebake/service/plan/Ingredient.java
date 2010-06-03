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

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * A step in a {@link Recipe recipe} that involves producing a {@link Product}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Ingredient {
  /** The name of the product that must be built to prepare this ingredient. */
  public final String product;
  /**
   * The ingredients whose preReq count can be decremented once this
   * ingredient is prepared.
   */
  public final ImmutableList<Ingredient> postRequisites;
  /**
   * The names of ingredients that must be prepared before this ingredient can
   * be prepared.
   */
  public final ImmutableList<String> preRequisites;

  public Ingredient(
      String product, ImmutableList<String> preReqs,
      ImmutableList<Ingredient> postReqs) {
    this.product = product;
    this.preRequisites = preReqs;
    this.postRequisites = postReqs;
  }
}
