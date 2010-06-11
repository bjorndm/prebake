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

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.ImmutableGlobSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * Maintains a sparse graph of product dependencies.
 *
 * <p>
 * An edge exists where the input globs of one nodes intersect with the output
 * globs of another.
 *
 * <p>
 * At any time the graph can be {@link #snapshot}ted as an
 * {@link PlanGraph immutable graph} that allows efficient edge traversal.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class PlanGrapher {
  /** The end points per product. */
  private final Map<String, EndPoints> nodes = Maps.newHashMap();
  /** Glob sets needed to compute the intersection graph of end-points. */
  private final Multiset<ImmutableGlobSet> endPoints = HashMultiset.create();
  /** True if there is an edge in the intersection graph of end-points. */
  private final Map<EndPoints, Boolean> edges = Maps.newHashMap();
  /** Products that have not been incorporated into the other collections. */
  private final Map<String, Product> unprocessed
      = Collections.synchronizedMap(Maps.<String, Product>newHashMap());

  final ArtifactListener<Product> productListener
      = new ArtifactListener<Product>() {
    public void artifactChanged(Product p) { unprocessed.put(p.name, p); }
    public void artifactDestroyed(String name) { unprocessed.put(name, null); }
  };

  public PlanGraph snapshot() {
    String[] productNames;
    EndPoints[] prodEndPoints;
    synchronized (this) {
      processProducts();
      productNames = nodes.keySet().toArray(NO_STRING);
      Arrays.sort(productNames);
      int n = productNames.length;
      prodEndPoints = new EndPoints[n];
      for (int i = n; --i >= 0;) {
        prodEndPoints[i] = nodes.get(productNames[i]);
      }
    }
    int n = productNames.length;
    PlanGraph.Builder b = PlanGraph.builder(productNames);
    for (int i = 0; i < n; ++i) {
      String p = productNames[i];
      ImmutableGlobSet sources = prodEndPoints[i].sources;
      for (int j = 0; j < n; ++j) {
        ImmutableGlobSet targets = prodEndPoints[j].targets;
        if (Boolean.TRUE.equals(edges.get(new EndPoints(sources, targets)))) {
          b.edge(productNames[j], p);
        }
      }
    }
    return b.build();
  }

  private synchronized void processProducts() {
    // This is O(n**2) in the number of products but minimizes the number of
    // glob intersection checks which are the expensive parts, and the number
    // of globs is strictly greater than the number of products.
    List<Product> prods = Lists.newArrayList();
    List<String> removed = Lists.newArrayList();
    synchronized (unprocessed) {
      for (Map.Entry<String, Product> e : unprocessed.entrySet()) {
        Product p = e.getValue();
        if (p != null) {
          prods.add(p);
        } else {
          removed.add(e.getKey());
        }
      }
      unprocessed.clear();
    }
    List<ImmutableGlobSet> oldGlobs = Lists.newArrayList();
    for (Product p : prods) {
      ImmutableGlobSet sources = p.getInputs();
      ImmutableGlobSet targets = p.getOutputs();
      EndPoints newNode = new EndPoints(sources, targets);
      EndPoints oldNode = nodes.get(p.name);
      if (newNode.equals(oldNode)) { continue; }
      nodes.put(p.name, newNode);
      if (oldNode != null) {
        oldGlobs.add(oldNode.sources);
        oldGlobs.add(oldNode.targets);
      }
      endPoints.add(newNode.sources);
      endPoints.add(newNode.targets);
    }
    for (String name : removed) {
      EndPoints oldNode = nodes.get(name);
      if (oldNode != null) {
        oldGlobs.add(oldNode.sources);
        oldGlobs.add(oldNode.targets);
      }
    }
    Set<ImmutableGlobSet> defunct = Sets.newHashSet();
    for (ImmutableGlobSet globs : oldGlobs) {
      if (endPoints.remove(globs, 1) == 0) { defunct.add(globs); }
    }
    if (!defunct.isEmpty()) {
      for (Iterator<EndPoints> it = edges.keySet().iterator(); it.hasNext();) {
        EndPoints ep = it.next();
        if (defunct.contains(ep.sources) || defunct.contains(ep.targets)) {
          it.remove();
        }
      }
    }
    ImmutableGlobSet[] globsArr = endPoints.toArray(NO_GLOBS);
    for (int n = globsArr.length, i = n; --i >= 0;) {
      for (int j = i; --j >= 0;) {
        if (j == i) { continue; }
        EndPoints ep = new EndPoints(globsArr[i], globsArr[j]);
        if (edges.containsKey(ep)) { continue; }
        Boolean inter = Glob.overlaps(ep.sources, ep.targets);
        edges.put(ep, inter);
        edges.put(new EndPoints(globsArr[j], globsArr[i]), inter);
      }
    }
  }

  private static final class EndPoints {
    final ImmutableGlobSet sources;
    final ImmutableGlobSet targets;

    EndPoints(ImmutableGlobSet sources, ImmutableGlobSet targets) {
      this.sources = sources;
      this.targets = targets;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof EndPoints)) { return false; }
      EndPoints that = (EndPoints) o;
      return this.sources.equals(that.sources)
          && this.targets.equals(that.targets);
    }

    @Override public int hashCode() {
      return sources.hashCode() + 31 * targets.hashCode();
    }
    @Override public String toString() {
      return "[" + sources + " -> " + targets + "]";
    }
  }

  private static final ImmutableGlobSet[] NO_GLOBS = new ImmutableGlobSet[0];
  private static final String[] NO_STRING = new String[0];
}
