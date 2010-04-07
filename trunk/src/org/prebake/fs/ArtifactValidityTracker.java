package org.prebake.fs;

import org.prebake.core.Hash;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Monitors the file system and stores the relationship between
 * non-file-artifacts and the files on which they depend so it can issue events
 * when non-file-artifacts become invalid.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface ArtifactValidityTracker extends Closeable {
  @Nonnull FileSystem getFileSystem();
  @Nonnull FileAndHash load(Path p) throws IOException;
  <T extends NonFileArtifact> boolean update(
      ArtifactAddresser<T> as, T artifact,
      Collection<Path> prerequisites, Hash prereqHash);
}
