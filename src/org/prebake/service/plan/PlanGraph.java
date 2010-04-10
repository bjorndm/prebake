package org.prebake.service.plan;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

/**
 * A directed graph where nodes are {@link Product}s and an edge exists between
 * to nodes where the source produces outputs that might be inputs to the
 * target.
 *
 * @author mikesamuel@gmail.com
 */
public final class PlanGraph {
  public final ImmutableSet<String> nodes;
  /**
   * Contains the name of each product that the key product depends upon
   * non-transitively.
   */
  public final ImmutableMultimap<String, String> edges;

  private PlanGraph(
      ImmutableSet<String> nodes, ImmutableMultimap<String, String> edges) {
    this.nodes = nodes;
    this.edges = edges;
  }

  public static class Builder {
    private final ImmutableSet<String> nodes;
    private final ImmutableMultimap.Builder<String, String> edges
        = ImmutableMultimap.builder();

    private Builder(ImmutableSet<String> nodes) { this.nodes = nodes; }

    public Builder edge(String from, String to) {
      edges.put(to, from);
      return this;
    }

    public PlanGraph build() {
      return new PlanGraph(nodes, edges.build());
    }
  }

  public static Builder builder(String... nodes) {
    return new Builder(ImmutableSet.of(nodes));
  }
}
