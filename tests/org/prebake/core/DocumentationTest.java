package org.prebake.core;

import org.prebake.js.JsonSink;
import org.prebake.js.YSON;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;

import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

public class DocumentationTest {
  @Test public final void testSummaryOf() {
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

  @Test public final void testConverter() {
    assertConverter(
        null, null,
        "Expected {\"detail\":<string>,\"summary\":<string>,...}, not null");
    assertConverter(
        null, "4",
        "Expected {\"detail\":<string>,\"summary\":<string>,...}, not 4.0");
    assertConverter(
        ""
        + "{"
        + "\"summary\":\"foobar baz.\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "\"contact\":null"
        + "}",
        "'foobar baz.  boo far faz.'");
    assertConverter(
        ""
        + "{"
        + "\"summary\":\"foobar baz.\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "\"contact\":null"
        + "}",
        "{detail:'foobar baz.  boo far faz.'}");
    assertConverter(
        ""
        + "{"
        + "\"summary\":\"foo bar\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "\"contact\":null"
        + "}",
        ""
        + "{"
        + "\"summary\":\"foo bar\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "\"contact\":null"
        + "}");
    assertConverter(
        ""
        + "{"
        + "\"summary\":\"foo bar\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "\"contact\":null"
        + "}",
        ""
        + "{"
        + "\"summary\":\"foo bar\","
        + "\"detail\":\"foobar baz.  boo far faz.\","
        + "}");
    assertConverter(
        "{\"summary\":\"s\",\"detail\":\"d\",\"contact\":\"c\"}",
        "{\"summary\":\"s\",\"detail\":\"d\",\"contact\":\"c\"}");
    assertConverter(
        null, "{\"summary\":\"s\",\"details\":\"d\",\"contact\":\"c\"}",
        ("Missing key detail in "
         + "{\"summary\":\"s\",\"details\":\"d\",\"contact\":\"c\"}"),
        "Unexpected key \"details\". Did you mean \"detail\"?");
  }

  private void assertConverter(String golden, String yson, String... messages) {
    Object input;
    try {
      input = yson != null ? YSON.parseExpr(yson).toJavaObject() : null;
    } catch (ParseException ex) {
      fail(ex.toString());
      return;
    }
    MessageQueue mq = new MessageQueue();
    Documentation doc = Documentation.CONVERTER.convert(input, mq);
    String msgs = Joiner.on('\n').join(mq.getMessages());
    String actual = null;
    if (doc != null) {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      try {
        doc.toJson(sink, true);
      } catch (IOException ex) {
        Throwables.propagate(ex);
      }
      actual = sb.toString();
    }
    assertEquals(msgs, golden, actual);
    if (messages != null && actual != null) {
      assertEquals(msgs, messages.length != 0, mq.hasErrors());
      assertEquals(Joiner.on('\n').join(messages), msgs);
      // test idempotency
      assertConverter(actual, actual, (String[]) null);
      if (doc != null) {
        String shortForm = doc.toString();
        if (!shortForm.equals(actual)) {
          assertConverter(actual, shortForm, (String[]) null);
        }
      }
    }
  }
}
