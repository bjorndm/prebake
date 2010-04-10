package org.prebake.fs;

import org.prebake.core.Hash;

import java.nio.file.Path;
import java.util.Collection;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface FileVersioner {
  public void getHashes(Collection<Path> paths, Hash.Builder out);
}
