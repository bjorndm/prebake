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

package org.prebake.fs;

import org.prebake.core.Hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.io.ByteStreams;

/**
 * The contents of a file and its hash if it is under the project root.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class FileAndHash {
  private final Path p;
  private final byte[] content;
  @Nullable private final Hash hash;

  public static FileAndHash fromStream(Path p, InputStream in, boolean makeHash)
      throws IOException {
    try {
      byte[] bytes = ByteStreams.toByteArray(in);
      Hash hash = null;
      if (makeHash) {
        hash = Hash.builder().withData(bytes).build();
      }
      return new FileAndHash(p, bytes, hash);
    } finally {
      in.close();
    }
  }

  private FileAndHash(Path p, byte[] content, @Nullable Hash hash) {
    this.p = p;
    this.content = content;
    this.hash = hash;
  }

  public Path getPath() { return p; }
  public String getContentAsString(Charset encoding) {
    // TODO: take a null encoding where it is not known and examine
    // the content prefix to figure out the charset.
    return new String(content, encoding);
  }
  public @Nullable Hash getHash() { return hash; }

  public FileAndHash withoutHash() {
    return hash == null ? this : new FileAndHash(p, content, null);
  }
}
