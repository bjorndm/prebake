package org.prebake.core;

import org.prebake.util.PbTestCase;

import java.text.MessageFormat;

import org.junit.Test;

public class MessageQueueTest extends PbTestCase {
  @Test public final void testEscape() {
    assertEscapedProperly("");
    assertEscapedProperly("foo");
    assertEscapedProperly("foo{0}bar");
    assertEscapedProperly("{foobar}");
    assertEscapedProperly("foo{bar");
    assertEscapedProperly("foo}bar");
  }

  private void assertEscapedProperly(String s) {
    assertEquals(
        s, MessageFormat.format(MessageQueue.escape(s), new Object[0]));
  }
}
