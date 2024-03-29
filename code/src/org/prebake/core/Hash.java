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

package org.prebake.core;

import com.google.common.base.Throwables;
import com.sleepycat.je.DatabaseEntry;
import com.twmacinta.util.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An MD5 hash generated from files, strings, or groups of hashes.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Hash {
  private final byte[] bytes;

  private Hash(byte[] bytes) { this.bytes = bytes; }

  public DatabaseEntry toDatabaseEntry() {
    return new DatabaseEntry(bytes);
  }

  public static Hash fromDatabaseEntry(DatabaseEntry entry) {
    return new Hash(entry.getData());
  }

  public boolean matches(byte[] bytes) {
    return Arrays.equals(this.bytes, bytes);
  }

  public String toHexString() { return MD5.asHex(bytes); }

  @Override
  public boolean equals(Object o) {
    return o instanceof Hash && Arrays.equals(bytes, ((Hash) o).bytes);
  }

  @Override public int hashCode() { return Arrays.hashCode(bytes); }

  @Override
  public String toString() { return "[Hash " + Arrays.toString(bytes) + "]"; }

  private static final byte[] ZERO_BYTE = new byte[1];

  public static Builder builder() { return new Builder(); }

  @ParametersAreNonnullByDefault
  public static class Builder {
    private MD5 md5 = new MD5();

    private Builder() { /* not public */ }

    public Builder withFile(Path p) throws IOException {
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

    public Builder withString(String s) {
      md5.Update(ZERO_BYTE, 0, 1);
      try {
        md5.Update(s, "UTF-8");
      } catch (UnsupportedEncodingException ex) {
        Throwables.propagate(ex);  // UTF-8 must be supported
      }
      return this;
    }

    public Builder withData(byte[] bytes) {
      md5.Update(ZERO_BYTE, 0, 1);
      md5.Update(bytes, 0, bytes.length);
      return this;
    }

    public Builder withHash(Hash h) { return withData(h.bytes); }

    public Hash build() { return new Hash(md5.Final()); }
  }
}
