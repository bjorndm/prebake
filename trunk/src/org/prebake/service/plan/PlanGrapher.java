package org.prebake.service.plan;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
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
 * @author mikesamuel@gmail.com
 */
public final class PlanGrapher {
  /** The end points per product. */
  private final Map<String, EndPoints> nodes = Maps.newHashMap();
  /** Glob sets needed to compute the intersection graph of end-points. */
  private final Multiset<Globs> endPoints = HashMultiset.create();
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

  public synchronized PlanGraph snapshot() {
    processProducts();
    String[] productNames = nodes.keySet().toArray(NO_STRING);
    Arrays.sort(productNames);
    int n = productNames.length;
    EndPoints[] prodEndPoints = new EndPoints[n];
    for (int i = n; --i >= 0;) {
      prodEndPoints[i] = nodes.get(productNames[i]);
    }
    PlanGraph.Builder b = PlanGraph.builder(productNames);
    for (int i = 0; i < n; ++i) {
      String p = productNames[i];
      Globs sources = prodEndPoints[i].sources;
      for (int j = 0; j < n; ++j) {
        Globs targets = prodEndPoints[j].targets;
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
    List<Globs> oldGlobs = Lists.newArrayList();
    for (Product p : prods) {
      Globs sources = new Globs(p.inputs);
      Globs targets = new Globs(p.outputs);
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
    Set<Globs> defunct = Sets.newHashSet();
    for (Globs globs : oldGlobs) {
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
    Globs[] globsArr = endPoints.toArray(NO_GLOBS);
    for (int n = globsArr.length, i = n; --i >= 0;) {
      for (int j = i; --j >= 0;) {
        if (j == i) { continue; }
        EndPoints ep = new EndPoints(globsArr[i], globsArr[j]);
        if (edges.containsKey(ep)) { continue; }
        Boolean inter = Glob.overlaps(ep.sources.globs, ep.targets.globs)
            ? Boolean.TRUE : Boolean.FALSE;
        edges.put(ep, inter);
        edges.put(new EndPoints(globsArr[j], globsArr[i]), inter);
      }
    }
  }

  private static final class Globs {
    final ImmutableList<Glob> globs;
    final int hashCode;

    Globs(List<Glob> globs) {
      Glob[] globArr = globs.toArray(NO_GLOB);
      Arrays.sort(globArr);
      this.globs = ImmutableList.of(globArr);
      this.hashCode = globs.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Globs)) { return false; }
      Globs that = (Globs) o;
      return this.hashCode == that.hashCode && this.globs.equals(that.globs);
    }
    @Override public int hashCode() { return hashCode; }
    @Override public String toString() { return globs.toString(); }
  }

  private static final class EndPoints {
    final Globs sources;
    final Globs targets;

    EndPoints(Globs sources, Globs targets) {
      this.sources = sources;
      this.targets = targets;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof EndPoints)) { return false; }
      EndPoints that = (EndPoints) o;
      return this.targets.globs.size() == that.targets.globs.size()
          && this.sources.equals(that.sources)
          && this.targets.equals(that.targets);
    }

    @Override public int hashCode() {
      return sources.hashCode + 31 * targets.hashCode;
    }
    @Override public String toString() {
      return "[" + sources + " -> " + targets + "]";
    }
  }

  private static final Glob[] NO_GLOB = new Glob[0];
  private static final Globs[] NO_GLOBS = new Globs[0];
  private static final String[] NO_STRING = new String[0];
}
