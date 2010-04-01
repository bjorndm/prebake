package org.prebake.core;

import junit.framework.TestCase;

public class DidYouMeanTest extends TestCase {
  public final void testToMessage() {
    assertEquals(
        "Foo. Did you mean \"cookies\"?",
        DidYouMean.toMessage(
            "Foo", "cake", "ice cream", "cookies", "apple pie"));
    assertEquals(
        "Bad flag -Foo. Did you mean \"--foo\"?",
        DidYouMean.toMessage(
            "Bad flag -Foo", "--Foo",
            "--foo", "--bar", "--baz"));
    assertEquals(
        "Bad flag -Bar. Did you mean \"--bar\"?",
        DidYouMean.toMessage(
            "Bad flag -Bar", "--Bar",
            "--foo", "--bar", "--baz"));
    assertEquals(
        "Bad flag -Baz. Did you mean \"--baz\"?",
        DidYouMean.toMessage(
            "Bad flag -Baz", "--Baz",
            "--foo", "--bar", "--baz"));
  }
}