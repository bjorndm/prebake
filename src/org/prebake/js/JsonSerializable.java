package org.prebake.js;

import java.io.IOException;

public interface JsonSerializable {
  void toJson(JsonSink sink) throws IOException;

  public static final class StringUtil {
    public static String toString(JsonSerializable o) {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      try {
        o.toJson(sink);
        sink.close();
      } catch (IOException ex) {
        throw new RuntimeException(ex);  // writing to in-memory buffer
      }
      return sb.toString();
    }

    private StringUtil() { /* not instantiable */ }
  }
}
