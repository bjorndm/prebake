package org.prebake.fs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A reversible mapping from {@link NonFileArtifact}s to addresses.
 *
 * @author mikesamuel@gmail.com
 */
public interface ArtifactAddresser<T extends NonFileArtifact> {
  @Nullable T lookup(@Nonnull String address);
  @Nonnull String addressFor(@Nonnull T artifact);
}
