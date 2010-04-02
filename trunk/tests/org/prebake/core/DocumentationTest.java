package org.prebake.core;

import junit.framework.TestCase;

public class DocumentationTest extends TestCase {
  public final void testSummaryOf() {
    assertEquals("", Documentation.summaryOf(""));
    assertEquals("foo", Documentation.summaryOf("foo"));
    assertEquals("foo.", Documentation.summaryOf("foo."));
    assertEquals(".", Documentation.summaryOf("."));
    assertEquals(".", Documentation.summaryOf(". foo"));
    assertEquals(
        "Zounds!  Egad!  Amazing!",
        Documentation.summaryOf("Zounds!  Egad!  Amazing!"));
    assertEquals(
        "This frobbits the widget.",
        Documentation.summaryOf(
            "This frobbits the widget.  Longer explanation follows."));
    assertEquals(
        "This is a simulation of Prof.<!-- --> Knuth's MIX computer.",
        Documentation.summaryOf(
            "This is a simulation of Prof.<!-- --> Knuth's MIX computer.  "
            + "Longer explanation follows."));
    assertEquals(
        "This is a simulation of Prof.<!-- --> Knuth's MIX computer",
        Documentation.summaryOf(
            "This is a simulation of Prof.<!-- --> Knuth's MIX computer\n"
            + "@see KnuthIsMyHomeboy"));
  }
}
