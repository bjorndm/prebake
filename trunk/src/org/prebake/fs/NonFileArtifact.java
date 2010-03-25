package org.prebake.fs;

/**
 * An artifact derived from files that is not represented in the file-system
 * itself, e.g. a tool definition, a product,
 * or an edge in the dependency graph.
 *
 * @author mikesamuel@gmail.com
 */
public interface NonFileArtifact {
  void markValid(boolean valid);
}
