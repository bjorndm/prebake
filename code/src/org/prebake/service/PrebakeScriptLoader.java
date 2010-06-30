// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.prebake.service;

import org.prebake.core.Hash;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.Loader;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

/**
 * A loader implementation that tries to satisfy {@code load} requests based on
 * the {@link BuiltinResourceLoader} or a {@link FileVersioner} as appropriate,
 * and that keeps track of the versions of files loaded so that
 * {@link org.prebake.fs.NonFileArtifact}s can be kept
 * {@link FileVersioner#updateArtifact up-to-date}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class PrebakeScriptLoader implements Loader {
  private final FileVersioner files;
  private final ImmutableList.Builder<Path> paths;
  private final Hash.Builder hashes;

  public PrebakeScriptLoader(
      FileVersioner files,
      ImmutableList.Builder<Path> paths,
      Hash.Builder hashes) {
    this.files = files;
    this.paths = paths;
    this.hashes = hashes;
  }

  public Executor.Input load(Path p) throws IOException {
    FileAndHash fh = BuiltinResourceLoader.tryLoad(p);
    if (fh == null) {
      try {
        fh = files.load(p);
      } catch (IOException ex) {
        // We need to depend on non-existent files in case they
        // are later created.
        if (files.isUnderVersionRoot(p)) {
          paths.add(p);
          hashes.withHash(FileVersioner.NO_FILE_HASH);
        }
        throw ex;
      }
    }
    if (fh.getHash() != null) {
      paths.add(fh.getPath());
      hashes.withHash(fh.getHash());
    }
    return Executor.Input
        .builder(fh.getContentAsString(Charsets.UTF_8), p).build();
  }
}
