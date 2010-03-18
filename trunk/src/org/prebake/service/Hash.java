package org.prebake.service;

import com.twmacinta.util.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class Hash {
  private final byte[] bytes;

  private Hash(byte[] bytes) {
    this.bytes = bytes;
  }

  byte[] getBytes() { return bytes; }

  private static final byte[] ZERO_BYTE = new byte[1];

  static class HashBuilder {
    private MD5 md5 = new MD5();

    HashBuilder withFile(Path p) throws IOException {
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

    HashBuilder withString(String s) throws IOException {
      md5.Update(ZERO_BYTE, 0, 1);
      md5.Update(s, "UTF-8");
      return this;
    }

    HashBuilder withData(byte[] bytes) throws IOException {
      md5.Update(ZERO_BYTE, 0, 1);
      md5.Update(bytes, 0, bytes.length);
      return this;
    }

    Hash toHash() {
      Hash h = new Hash(md5.Final());
      md5 = new MD5();
      return h;
    }
  }
}
