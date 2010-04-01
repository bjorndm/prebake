package org.prebake.js;

import java.io.IOException;
import java.io.StringReader;

import junit.framework.TestCase;

public class JsonSourceTest extends TestCase {
  public final void testJsonSource() throws Exception {
    JsonSource src = new JsonSource(new StringReader(
        "false, \"bar\" : 1.5 [\"\\\"boo[]{}\\u000a\"] -4 1e-3"));
    assertFalse(src.isEmpty());
    assertEquals(Boolean.FALSE, src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals(",", src.next());
    assertFalse(src.isEmpty());
    assertEquals("bar", src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals(":", src.next());
    assertFalse(src.isEmpty());
    assertEquals(1.5, src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals("[", src.next());
    assertFalse(src.isEmpty());
    assertEquals("\"boo[]{}\n", src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals("]", src.next());
    assertFalse(src.isEmpty());
    assertEquals(Double.valueOf(-4), src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals(Double.valueOf(.001), src.nextValue());
    assertTrue(src.isEmpty());
  }

  public final void testBadJson() {
    assertBadJson(".", ".");
    assertBadJson(".0", ".");
    assertBadJson("0123", "0123");
    assertBadJson("falsey", "falsey");
    assertBadJson("1 * 1", "*");
    assertBadJson("{ \"foo\"# \"bar\" }", "#");
  }

  private void assertBadJson(String s, String problem) {
    JsonSource src = new JsonSource(new StringReader(s));
    try {
      while (!src.isEmpty()) { src.next(); }
    } catch (IOException ex) {
      assertEquals(problem, ex.getMessage());
      return;
    }
    fail(s);
  }
}
