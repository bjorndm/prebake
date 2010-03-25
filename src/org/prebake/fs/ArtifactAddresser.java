package org.prebake.fs;

/**
 * A reversible mapping from {@link NonFileArtifacts} to addresses.
 *
 * @author mikesamuel@gmail.com
 */
public interface ArtifactAddresser<T extends NonFileArtifact> {
  T lookup(String address);
  String addressFor(T artifact);
}
