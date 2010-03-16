package org.prebake.channel;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class JsonSink implements Closeable {
  private final Appendable out;

  public JsonSink(Appendable out) { this.out = out; }

  public void close() throws IOException {
    if (out instanceof Closeable) {
      ((Closeable) out).close();
    }
  }

  public JsonSink write(String s) throws IOException {
    out.append(s);
    return this;
  }

  public JsonSink writeValue(String s) throws IOException {
    out.append('"');
    int pos = 0, n = s.length();
    for (int i = 0; i < n; ++i) {
      int sub = -1;
      char ch = s.charAt(i);
      switch (s.charAt(i)) {
        case '"': case '\\': sub = ch; break;
        case '\n': sub = 'n'; break;
        case '\r': sub = 'r'; break;
        case '\f': sub = 'f'; break;
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

  public <T> JsonSink writeValue(Map<String, T> obj) throws IOException {
    out.append('{');
    Iterator<Map.Entry<String, T>> it = obj.entrySet().iterator();
    if (it.hasNext()) {
      Map.Entry<String, T> e = it.next();
      writeValue(e.getKey()).write(":").writeValue(e.getValue());
      while (it.hasNext()) {
        e = it.next();
        write(",").writeValue(e.getKey()).write(":").writeValue(e.getValue());
      }
    }
    out.append('}');
    return this;
  }

  public JsonSink writeValue(Iterable<?> obj) throws IOException {
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

  @SuppressWarnings("unchecked")
  public JsonSink writeValue(Object o) throws IOException {
    if (o == null) {
      out.append("null");
      return this;
    } else if (o instanceof String) {
      return writeValue((String) o);
    } else if (o instanceof Map<?, ?>) {
      return writeValue((Map<String, ?>) o);
    } else if (o instanceof List<?>) {
      return writeValue((List<?>) o);
    } else if (o instanceof Boolean) {
      return writeValue(((Boolean) o).booleanValue());
    } else {
      return write("" + o);
    }
  }

  public JsonSink writeValue(boolean b) throws IOException {
    out.append(b ? "true" : "false");
    return this;
  }
}
