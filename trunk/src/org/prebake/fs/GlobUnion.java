package org.prebake.fs;

import org.prebake.core.Glob;

import com.google.common.collect.ImmutableList;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A named group of {@link Glob}s corresponding to the inputs of a product or
 * action.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class GlobUnion {
  public final String name;
  public final ImmutableList<Glob> globs;
  private final int hashCode;

  public GlobUnion(String name, Iterable<Glob> globs) {
    this.name = name;
    this.globs = ImmutableList.copyOf(globs);
    this.hashCode = globs.hashCode() + 31 * name.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (!(o instanceof GlobUnion)) { return false; }
    GlobUnion that = (GlobUnion) o;
    return name.equals(that.name) && globs.equals(that.globs);
  }

  @Override
  public int hashCode() { return hashCode; }
}
