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
 * @author mikesamuel@gmail.com
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
    this.content = content;  // TODO
    this.hash = hash;
  }

  public Path getPath() { return p; }
  public String getContentAsString(Charset encoding) {
    // TODO: do the usual first few bytes check.
    return new String(content, encoding);
  }
  public @Nullable Hash getHash() { return hash; }
}
