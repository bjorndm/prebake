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

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;
import org.prebake.js.YSON;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.caja.lexer.escaping.Escaping;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * A name and parameters represented as an unordered key/value map of strings.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class BoundName
    implements Comparable<BoundName>, JsonSerializable {
  /** Canonical. */
  @Nonnull public final String ident;
  /** Keys sorted. */
  @Nonnull public final ImmutableMap<String, String> bindings;

  private BoundName(String ident, ImmutableMap<String, String> bindings) {
    this.ident = canonIdent(ident, bindings);
    this.bindings = bindings;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.writeValue(ident);
  }

  public int compareTo(BoundName that) {
    return this.ident.compareTo(that.ident);
  }

  @Override public boolean equals(Object o) {
    return o != null && o.getClass() == getClass()
        && this.ident.equals(((BoundName) o).ident);
  }

  @Override public int hashCode() { return ident.hashCode(); }

  @Override public String toString() { return ident; }

  public BoundName withBindings(Map<String, String> bindings) {
    ImmutableMap<String, String> newBindings = ImmutableMap.copyOf(
        new TreeMap<String, String>(bindings));
    return new BoundName(getRawIdent(), newBindings);
  }

  public String getRawIdent() {
    return bindings.isEmpty() ? ident : ident.substring(0, ident.indexOf('['));
  }

  /**
   * Converts a string like {@code "foo"} or {@code "bar[\"x\":\"y\"]"} into
   * a bound identifier.
   * <p>
   * This factory function has the signature that
   * {@link org.prebake.js.YSONConverter.Factory#withType} expects.
   */
  public static BoundName fromString(String s) {
    int lbracket = s.indexOf('[');
    String ident;
    ImmutableMap<String, String> bindings;
    if (lbracket < 0) {
      ident = s;
      // Optimization for common case.
      bindings = ImmutableMap.of();
    } else {
      ident = s.substring(0, lbracket);
      JsonSource src = new JsonSource(
          new StringReader(s.substring(lbracket + 1)));
      try {
        if (src.check("]")) {
          bindings = ImmutableMap.of();
        } else {
          SortedMap<String, String> b = Maps.newTreeMap();
          do {
            String key = src.expectString();
            src.expect(":");
            String value = src.expectString();
            String old = b.put(key, value);
            if (old != null && !old.equals(value)) {
              throw new IllegalArgumentException(
                  "Duplicate binding " + JsonSink.stringify(key)
                  + " with values " + JsonSink.stringify(old)
                  + " and " + JsonSink.stringify(value) + " for " + s);
            }
          } while (src.check(","));
          src.expect("]");
          bindings = ImmutableMap.copyOf(b);
        }
      } catch (IOException ex) {
        // Propagate syntax errors as IllegalArgumentExceptions
        throw new IllegalArgumentException(ex.getMessage(), ex);
      }
    }
    if (!YSON.isValidDottedIdentifierName(ident)) {
      throw new IllegalArgumentException(s + " is not a valid identifier");
    }
    return new BoundName(ident, bindings);
  }

  private static String canonIdent(
      String ident, Map<String, String> sortedBindings) {
    if (sortedBindings.isEmpty()) { return ident; }
    StringBuilder sb = new StringBuilder(
        ident.length() + 16 * sortedBindings.size());
    sb.append(ident);
    char sep = '[';
    for (Map.Entry<String, String> binding : sortedBindings.entrySet()) {
      sb.append(sep).append('"');
      Escaping.escapeJsString(binding.getKey(), false, false, sb);
      sb.append("\":\"");
      Escaping.escapeJsString(binding.getValue(), false, false, sb);
      sb.append('"');

      sep = ',';
    }
    return sb.append(']').toString();
  }
}
