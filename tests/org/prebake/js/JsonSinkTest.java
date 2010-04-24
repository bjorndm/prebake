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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import javax.annotation.Nullable;

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

  private void assertValueJson(String golden, @Nullable Object o)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    JsonSink sink = new JsonSink(sb);
    sink.writeValue(o);
    sink.close();
    assertEquals(golden, sb.toString());
  }
}
