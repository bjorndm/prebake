package org.prebake.core;

import com.twmacinta.util.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class Hash {
  private final byte[] bytes;

  private Hash(byte[] bytes) { this.bytes = bytes; }

  public byte[] getBytes() { return bytes; }

  @Override
  public boolean equals(Object o) {
    return o instanceof Hash && Arrays.equals(bytes, ((Hash) o).bytes);
  }

  @Override public int hashCode() { return Arrays.hashCode(bytes); }

  @Override
  public String toString() { return "[Hash " + Arrays.toString(bytes) + "]"; }

  private static final byte[] ZERO_BYTE = new byte[1];

  public static class HashBuilder {
    private MD5 md5 = new MD5();

    public HashBuilder withFile(Path p) throws IOException {
      md5.Update(ZERO_BYTE, 0, 1);
      byte[] bytes = new byte[4096];
      InputStream in = p.newInputStream(StandardOpenOption.READ);
      try {
        for (int n; (n = in.read(bytes)) > 0;) { md5.Update(bytes, 0, n); }
      } finally {
        in.close();
      }
      return this;
    }

    public HashBuilder withString(String s) throws IOException {
      md5.Update(ZERO_BYTE, 0, 1);
      md5.Update(s, "UTF-8");
      return this;
    }

    public HashBuilder withData(byte[] bytes) {
      md5.Update(ZERO_BYTE, 0, 1);
      md5.Update(bytes, 0, bytes.length);
      return this;
    }

    public Hash toHash() { return new Hash(md5.Final()); }
  }
}
