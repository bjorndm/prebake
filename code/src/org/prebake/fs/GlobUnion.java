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

package org.prebake.fs;

import org.prebake.core.BoundName;
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
  public final BoundName name;
  public final ImmutableList<Glob> globs;
  private final int hashCode;

  public GlobUnion(BoundName name, Iterable<Glob> globs) {
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
