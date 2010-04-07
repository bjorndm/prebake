package org.prebake.fs;

import org.prebake.core.Hash;

import java.nio.charset.Charset;
import java.nio.file.Path;

public final class FileAndHash {
  private final Path p;
  private final byte[] content;
  private final Hash hash;

  public FileAndHash(Path p, byte[] content, Hash hash) {
    this.p = p;
    this.content = content;  // TODO
    this.hash = hash;
  }

  public Path getPath() { return p; }
  public String getContentAsString(Charset encoding) {
    return new String(content, encoding);
  }
  public Hash getHash() { return hash; }
}
