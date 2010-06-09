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

package org.prebake.core;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A mutable {@link GlobSet}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class MutableGlobSet extends GlobSet {
  public MutableGlobSet() { /* no-op */ }

  /** A copy of the given glob set. */
  public MutableGlobSet(GlobSet gset) { super(gset); }

  @Override public void add(Glob glob) { super.add(glob); }

  /**
   * Adds all the given globs.
   * @see #add
   * @see #remove
   */
  public GlobSet addAll(Iterable<Glob> globs) {
    for (Glob g : globs) { add(g); }
    return this;
  }

  @Override public boolean remove(Glob glob) { return super.remove(glob); }
}
