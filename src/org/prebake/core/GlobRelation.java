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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A parameterized relationship between sets of globs.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class GlobRelation {
  public final ImmutableGlobSet inputs;
  public final ImmutableGlobSet outputs;
  public final ImmutableMap<String, Param> parameters;

  /**
   * Meta-data about a named glob parameter,
   * e.g. x in {@code out/arch/*(x)/lib/}.
   */
  public static final class Param {
    public final String name;
    public final @Nullable ImmutableSet<String> allowedValues;

    public Param(
        String name, @Nullable Iterable<? extends String> allowedValues) {
      this.name = name;
      this.allowedValues = allowedValues != null
          ? ImmutableSet.copyOf(allowedValues) : null;
    }
  }

  /** The result of binding the free parameters in a glob relation. */
  public static final class Solution {
    public final ImmutableGlobSet inputs;
    public final ImmutableGlobSet outputs;
    public final ImmutableMap<String, String> parameterBindings;
    Solution(GlobSet inputs, GlobSet outputs,
        Map<String, String> bindings) {
      this.inputs = ImmutableGlobSet.copyOf(inputs);
      this.outputs = ImmutableGlobSet.copyOf(outputs);
      this.parameterBindings = ImmutableMap.copyOf(bindings);
    }
  }

  public GlobRelation(
      GlobSet inputs, GlobSet outputs, Iterable<? extends Param> parameters) {
    this.inputs = ImmutableGlobSet.copyOf(inputs);
    this.outputs = ImmutableGlobSet.copyOf(outputs);
    Set<String> free = Sets.newHashSet();
    findFreeParameterNames(this.inputs, free);
    findFreeParameterNames(this.outputs, free);
    ImmutableMap.Builder<String, Param> b = ImmutableMap.builder();
    for (Param p : parameters) {
      free.remove(p.name);
      b.put(p.name, p);
    }
    for (String unspecified : free) {
      b.put(unspecified, new Param(unspecified, null));
    }
    this.parameters = b.build();
  }

  public @Nullable Solution solveForOutput(Path p) {
    Map<String, String> bindings = Maps.newLinkedHashMap();
    Iterator<Glob> matching = outputs.matching(p).iterator();
    if (!matching.hasNext()) { return null; }
    if (parameters.isEmpty()) {
      return new Solution(inputs, outputs, ImmutableMap.<String, String>of());
    }
    String pathStr = p.toString();
    do {
      Glob g = matching.next();
      if (!g.match(pathStr, bindings)) { return null; }
    } while (matching.hasNext());
    // Not all parameters matched.
    if (bindings.size() != parameters.size()) { return null; }
    return withParameterValues(bindings);
  }

  public @Nonnull Solution withParameterValues(Map<String, String> bindings) {
    ImmutableList.Builder<Glob> solvedInputs = ImmutableList.builder();
    ImmutableList.Builder<Glob> solvedOutputs = ImmutableList.builder();
    for (Glob g : inputs) { solvedInputs.add(g.subst(bindings)); }
    for (Glob g : outputs) { solvedOutputs.add(g.subst(bindings)); }
    return new Solution(
        ImmutableGlobSet.of(solvedInputs.build()),
        ImmutableGlobSet.of(solvedOutputs.build()),
        bindings);
  }

  public @Nullable ImmutableList<Solution> allPossibleSolutions() {
    ImmutableList<String> keys;
    ImmutableList<Set<String>> valueSets;
    {
      ImmutableList.Builder<String> kb = ImmutableList.builder();
      ImmutableList.Builder<Set<String>> vb = ImmutableList.builder();
      for (Param p : parameters.values()) {
        if (p.allowedValues == null) { return null; }
        kb.add(p.name);
        vb.add(p.allowedValues);
      }
      keys = kb.build();
      valueSets = vb.build();
    }
    Map<String, String> bindings = Maps.newLinkedHashMap();
    ImmutableList.Builder<Solution> solutions = ImmutableList.builder();
    int n = keys.size();
    for (List<String> values : Sets.cartesianProduct(valueSets)) {
      for (int i = 0; i < n; ++i) {
        bindings.put(keys.get(i), values.get(i));
        solutions.add(withParameterValues(bindings));
      }
    }
    return solutions.build();
  }

  private static void findFreeParameterNames(
      ImmutableGlobSet gset, Collection<? super String> free) {
    for (Glob g : gset) { g.enumerateHoleNamesOnto(free); }
  }
}
