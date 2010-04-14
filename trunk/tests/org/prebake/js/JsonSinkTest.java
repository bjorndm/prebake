package org.prebake.js;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonSinkTest {
  @Test public final void testJsonSink() throws IOException {
    assertValueJson("false", false);
    assertValueJson("true", true);
    assertValueJson("null", null);
    assertValueJson("-0.0", -0d);
    assertValueJson("NaN", Double.NaN);
    assertValueJson("Infinity", Double.POSITIVE_INFINITY);
    assertValueJson("[null,1.0,-2.0,3.5]", Arrays.asList(null,1.,-2.,3.5));
    assertValueJson("\"\\n\"", "\n");
    assertValueJson("\"\\r\"", "\r");
    assertValueJson("\"\\f\"", "\f");
    assertValueJson("\"\\b\"", "\b");
    assertValueJson("\"\\t\"", "\t");
    assertValueJson("\"foo\"", "foo");
    assertValueJson(
        "[\"bar\",[false,true],null,{}]",
        Arrays.<Object>asList(
            "bar",new boolean[] { false, true }, null,
            Collections.emptyMap()));
  }

  private void assertValueJson(String golden, Object o) throws IOException {
    StringBuilder sb = new StringBuilder();
    JsonSink sink = new JsonSink(sb);
    sink.writeValue(o);
    sink.close();
    assertEquals(golden, sb.toString());
  }
}
