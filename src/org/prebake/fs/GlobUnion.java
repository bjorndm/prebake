package org.prebake.fs;

import org.prebake.core.Glob;

import com.google.common.collect.ImmutableList;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A named group of {@link Glob}s corresponding to the inputs of a product or
 * action.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class GlobUnion {
  public final String name;
  public final ImmutableList<Glob> globs;

  public GlobUnion(String name, Iterable<Glob> globs) {
    this.name = name;
    this.globs = ImmutableList.copyOf(globs);
  }
}
