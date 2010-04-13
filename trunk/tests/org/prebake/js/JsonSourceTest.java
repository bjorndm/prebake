package org.prebake.js;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;


public class JsonSourceTest {
  @Test public final void testJsonSource() throws Exception {
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
    assertEquals(Long.valueOf(-4), src.nextValue());
    assertFalse(src.isEmpty());
    assertEquals(Double.valueOf(.001), src.nextValue());
    assertTrue(src.isEmpty());
  }

  @Test public final void testNumbers() throws Exception {
    JsonSource src = new JsonSource(new StringReader(
        "-0, 0.0, 0, 1e2, 2.5e-3, 0.5, -2, " + 0xfedcba9876543210L));
    assertFalse(src.isEmpty());
    assertEquals(-0.0d, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(0.0d, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(0L, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(100d, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(2.5e-3d, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(0.5d, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(-2L, src.nextValue());
    assertEquals(",", src.next());
    assertEquals(0xfedcba9876543210L, src.nextValue());
    assertTrue(src.isEmpty());
  }

  @Test public final void testBadJson() {
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
