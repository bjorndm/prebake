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

package org.prebake.js;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Throwables;

/**
 * A JSON writer that doesn't require converting lists, maps, and arrays to
 * instances of some other class.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class JsonSink implements Closeable {
  private final Appendable out;

  public JsonSink(Appendable out) { this.out = out; }

  /**
   * Serializes one object to {@link YSON} onto out.
   * @throws IllegalArgumentException if o cannot be serialized.
   */
  public static void stringify(@Nullable Object o, StringBuilder out) {
    JsonSink sink = new JsonSink(out);
    try {
      sink.writeValue(o);
      sink.close();
    } catch (IOException ex) {
      Throwables.propagate(ex);  // Writing to an in-memory buffer.
    }
  }

  /**
   * Serializes one object to a {@link YSON} string.
   * @throws IllegalArgumentException if o cannot be serialized.
   */
  public static String stringify(@Nullable Object o) {
    StringBuilder sb = new StringBuilder();
    stringify(o, sb);
    return sb.toString();
  }

  public void close() throws IOException {
    if (out instanceof Closeable) {
      ((Closeable) out).close();
    }
  }

  public JsonSink write(String s) throws IOException {
    out.append(s);
    return this;
  }

  public JsonSink writeValue(@Nullable String s) throws IOException {
    if (s == null) {
      out.append("null");
      return this;
    }
    out.append('"');
    int pos = 0, n = s.length();
    for (int i = 0; i < n; ++i) {
      int sub;
      char ch = s.charAt(i);
      switch (s.charAt(i)) {
        case '\b': sub = 'b'; break;
        case '\t': sub = 't'; break;
        case '\n': sub = 'n'; break;
        case '\f': sub = 'f'; break;
        case '\r': sub = 'r'; break;
        case '"': case '\\': sub = ch; break;
        // If we want to embed the output in JavaScript then we need to escape
        // all JavaScript newlines.
        case '\u0085': case '\u2028': case '\u2029': sub = 'u'; break;
        default: sub = -1; break;
      }
      if (sub != -1) {
        out.append(s, pos, i);
        out.append('\\');
        out.append((char) sub);
        if (sub == 'u') {
          out.append(HEX[(ch >>> 12) & 0xf])
              .append(HEX[(ch >>> 8) & 0xf])
              .append(HEX[(ch >>> 4) & 0xf])
              .append(HEX[ch & 0xf]);
        }
        pos = i + 1;
      }
    }
    out.append(s, pos, n);
    out.append('"');
    return this;
  }

  private static final char[] HEX = new char[] {
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
  };

  public JsonSink writeValue(@Nullable Map<String, ?> obj) throws IOException {
    if (obj == null) {
      out.append("null");
      return this;
    }
    return writeMap(obj);
  }

  private <K, V> JsonSink writeMap(Map<K, V> obj) throws IOException {
    out.append('{');
    Iterator<Map.Entry<K, V>> it = obj.entrySet().iterator();
    if (it.hasNext()) {
      Map.Entry<K, V> e = it.next();
      Object k = e.getKey();
      if (k == null) {
        write("null");
      } else {
        writeValue(k.toString());
      }
      out.append(':');
      writeValue(e.getValue());
      while (it.hasNext()) {
        out.append(',');
        e = it.next();
        k = e.getKey();
        if (k == null) {
          write("null");
        } else {
          writeValue(k.toString());
        }
        out.append(':');
        writeValue(e.getValue());
      }
    }
    out.append('}');
    return this;
  }

  public JsonSink writeValue(@Nullable Iterable<?> obj) throws IOException {
    if (obj == null) {
      out.append("null");
      return this;
    }
    out.append('[');
    Iterator<?> it = obj.iterator();
    if (it.hasNext()) {
      writeValue(it.next());
      while (it.hasNext()) {
        out.append(',');
        writeValue(it.next());
      }
    }
    out.append(']');
    return this;
  }

  /**
   * @throws IllegalArgumentException if o cannot be serialized.
   */
  public JsonSink writeValue(@Nullable Object o) throws IOException {
    if (o == null) {
      out.append("null");
      return this;
    } else if (o instanceof String) {
      return writeValue((String) o);
    } else if (o instanceof Map<?, ?>) {
      return writeMap((Map<?, ?>) o);
    } else if (o instanceof Iterable<?>) {
      return writeValue((Iterable<?>) o);
    } else if (o instanceof Boolean) {
      return writeValue(((Boolean) o).booleanValue());
    } else if (o instanceof JsonSerializable) {
      ((JsonSerializable) o).toJson(this);
    } else if (o.getClass().isArray()) {
      int n = Array.getLength(o);
      out.append('[');
      if (n != 0) {
        writeValue(Array.get(o, 0));
        for (int i = 1; i < n; ++i) {
          out.append(',');
          writeValue(Array.get(o, i));
        }
      }
      out.append(']');
    } else if (o instanceof Number) {
      Number num = (Number) o;
      if (num instanceof Double || num instanceof Float) {
        double dv = num.doubleValue();
        long lv = (long) dv;
        if (representableAsLong(dv, lv)) {
          out.append(String.valueOf(lv));
          return this;
        }
      }
      out.append(num.toString());
    } else if (o instanceof Boolean) {
      out.append(o.toString());
    } else if (o instanceof Enum<?>) {
      return writeValue(((Enum<?>) o).name());
    } else {
      throw new IllegalArgumentException(
          "" + o + " : " + o.getClass().getName());
    }
    return this;
  }

  private static boolean representableAsLong(double d, long n) {
    // Work around findbugs check that, almost always correctly, warns about
    // comparing floating point values.
    // I'm not comparing two floating points arrived at by a different set of
    // computations.  Instead, I'm checking here whether d is integral
    // and representable as a long, so the usual caveats don't apply.
    return d - n == 0 && (n != 0 || (1.0d / d) >= 0);
  }

  public JsonSink writeValue(boolean b) throws IOException {
    out.append(b ? "true" : "false");
    return this;
  }

  public JsonSink writeValue(@Nullable JsonSerializable o) throws IOException {
    if (o != null) {
      o.toJson(this);
    } else {
      out.append("null");
    }
    return this;
  }
}
