package org.prebake.service.plan;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * An ordered list of {@link Product products} to build.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Ingredient {
  /** The name of the product that must be built to prepare this ingredient. */
  public final String product;
  /**
   * The Recipes whose required ingredients can be decremented once this
   * ingredient is prepared.
   */
  public final ImmutableList<Ingredient> postRequisites;
  /** The number of ingredients. */
  public final int nIngredients;

  public Ingredient(
      String product, int nIngredients, ImmutableList<Ingredient> postReqs) {
    this.product = product;
    this.nIngredients = nIngredients;
    this.postRequisites = postReqs;
  }
}
