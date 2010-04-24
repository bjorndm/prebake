package org.prebake.fs;

/**
 * An artifact derived from files that is not represented in the file-system
 * itself, e.g. a tool definition, a product,
 * or an edge in the dependency graph.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface NonFileArtifact {
  /**
   * @param valid false if the artifact is now invalid, and true if it is now
   *    valid.
   * @see FileVersioner#update(
   *     ArtifactAddresser, NonFileArtifact, java.util.Collection,
   *     org.prebake.core.Hash)
   */
  void markValid(boolean valid);
}
