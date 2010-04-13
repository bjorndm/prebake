package org.prebake.fs;

import org.prebake.core.Hash;

import java.nio.charset.Charset;
import java.nio.file.Path;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The contents of a file and its hash if it is under the project root.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class FileAndHash {
  private final Path p;
  private final byte[] content;
  @Nullable private final Hash hash;

  public FileAndHash(Path p, byte[] content, @Nullable Hash hash) {
    this.p = p;
    this.content = content;  // TODO
    this.hash = hash;
  }

  public Path getPath() { return p; }
  public String getContentAsString(Charset encoding) {
    return new String(content, encoding);
  }
  public @Nullable Hash getHash() { return hash; }
}
