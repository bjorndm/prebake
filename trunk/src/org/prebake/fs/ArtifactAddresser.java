package org.prebake.fs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A reversible mapping from {@link NonFileArtifact}s to addresses.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface ArtifactAddresser<T extends NonFileArtifact> {
  @Nullable T lookup(String address);
  @Nonnull String addressFor(T artifact);
}
