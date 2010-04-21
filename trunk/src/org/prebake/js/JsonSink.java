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
 * @author mikesamuel@gmail.com
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
        default: sub = -1; break;
      }
      if (sub != -1) {
        out.append(s, pos, i);
        out.append('\\');
        out.append((char) sub);
        pos = i + 1;
      }
    }
    out.append(s, pos, n);
    out.append('"');
    return this;
  }

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
      return this;
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
      return this;
    } else if (o instanceof Number || o instanceof Boolean) {
      out.append(o.toString());
      return this;
    } else if (o instanceof Enum<?>) {
      return writeValue(((Enum<?>) o).name());
    } else {
      throw new IllegalArgumentException(
          "" + o + " : " + o.getClass().getName());
    }
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
