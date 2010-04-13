package org.prebake.js;

import com.google.common.base.Throwables;

import java.io.IOException;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A JavaScript object that can be serialized to JSON.
 * See {@link YSONConverter} for the dual.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface JsonSerializable {
  void toJson(JsonSink sink) throws IOException;

  @ParametersAreNonnullByDefault
  public static final class StringUtil {
    public static String toString(JsonSerializable o) {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      try {
        o.toJson(sink);
        sink.close();
      } catch (IOException ex) {
        Throwables.propagate(ex);  // writing to in-memory buffer
      }
      return sb.toString();
    }

    private StringUtil() { /* not instantiable */ }
  }
}
