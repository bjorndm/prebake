package org.prebake.fs;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.Hash;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstracts away {@link FileHashes} so its dependencies can be tested
 * independently.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface FileVersioner {
  public Path getVersionRoot();
  public void getHashes(Collection<Path> paths, Hash.Builder out);
  public List<Path> matching(List<Glob> globs);
  public void watch(GlobUnion globs, ArtifactListener<GlobUnion> watcher);
  public void unwatch(GlobUnion globs, ArtifactListener<GlobUnion> watcher);
}
