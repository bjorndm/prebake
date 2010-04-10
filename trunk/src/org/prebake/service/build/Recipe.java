package org.prebake.service.build;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * An ordered list of {@link Product products} to build.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Recipe {
  public final ImmutableList<String> instructions;

  public Recipe(ImmutableList<String> instructions) {
    this.instructions = instructions;
  }
}
