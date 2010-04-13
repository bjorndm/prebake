package org.prebake.fs;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.Hash;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstracts away {@link FileHashes} so its dependencies can be tested
 * independently.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface FileVersioner {
  @Nonnull Path getVersionRoot();
  void getHashes(Collection<Path> paths, Hash.Builder out);
  @Nonnull List<Path> matching(List<Glob> globs);
  void watch(GlobUnion globs, ArtifactListener<GlobUnion> watcher);
  void unwatch(GlobUnion globs, ArtifactListener<GlobUnion> watcher);
  void update(Collection<Path> toUpdate);
  @Nonnull FileAndHash load(Path p) throws IOException;
}
