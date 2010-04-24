package org.prebake.fs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A reversible mapping from {@link NonFileArtifact}s to addresses.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface ArtifactAddresser<T extends NonFileArtifact> {
  /** Returns the artifact with the given address. */
  @Nullable T lookup(String address);
  /**
   * The address for the given artifact.
   * This is the dual of {@link #lookup} so
   *     {@code lookup(addressFor(artifact)) == artifact}.
   */
  @Nonnull String addressFor(T artifact);
}
