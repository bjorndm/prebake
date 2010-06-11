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

import java.util.Collections;
import java.util.Iterator;

import com.google.common.collect.ImmutableList;

/**
 * An immutable {@link GlobSet}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class ImmutableGlobSet extends GlobSet {
  private ImmutableGlobSet(GlobSet gset) { super(gset); }
  private ImmutableGlobSet(Iterable<? extends Glob> globs) {
    snapshot = ImmutableList.copyOf(globs);  // Preserve iteration order.
    for (Glob g : snapshot) { this.add(g); }
  }

  public static ImmutableGlobSet copyOf(GlobSet gset) {
    if (gset instanceof ImmutableGlobSet) { return (ImmutableGlobSet) gset; }
    return new ImmutableGlobSet(gset);
  }

  public static ImmutableGlobSet of(Iterable<? extends Glob> globs) {
    return new ImmutableGlobSet(globs);
  }

  public static ImmutableGlobSet empty() {
    return of(Collections.<Glob>emptySet());
  }

  private ImmutableList<Glob> snapshot;

  @Override public Iterator<Glob> iterator() {
    if (snapshot == null) { snapshot = snapshot(); }
    return snapshot.iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) { return false; }
    if (o == this) { return true; }
    ImmutableGlobSet that = (ImmutableGlobSet) o;
    Iterator<Glob> thisIt = this.iterator(), thatIt = that.iterator();
    while (thisIt.hasNext()) {
      if (!thatIt.hasNext()) { return false; }
      if (!thisIt.next().equals(thatIt.next())) { return false; }
    }
    return !thatIt.hasNext();
  }

  private int hc;
  @Override
  public int hashCode() {
    if (hc == 0) {
      int hc = 0;
      Iterator<Glob> thisIt = this.iterator();
      while (thisIt.hasNext()) {
        hc = hc * 31 + thisIt.next().hashCode();
      }
      if (hc == 0) { hc = 1; }
      this.hc = hc;
    }
    return hc;
  }
}
