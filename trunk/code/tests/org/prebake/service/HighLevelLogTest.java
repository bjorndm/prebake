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

package org.prebake.service;

import org.prebake.util.PbTestCase;
import org.prebake.util.TestClock;

import java.io.IOException;
import java.util.TimeZone;

import com.google.common.base.Joiner;

import org.junit.Test;

public final class HighLevelLogTest extends PbTestCase {
  @Test
  public final void testHighLevelLog() {
    long sec = 1000000000L;  // in ns.
    TestClock clock = new TestClock();
    HighLevelLog log = new HighLevelLog(clock);
    assertFormattedEvents(
        log,
        "<ul>",
        entry("19900101T000000Z", "19:00:00",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "Started"),
        "</ul>");
    clock.advance(3 * sec);
    log.planStatusChanged(clock.nanoTime(), "plan.js", true);
    clock.advance(60 * sec);
    log.productStatusChanged(clock.nanoTime(), "foo", true);
    log.productStatusChanged(clock.nanoTime(), "bar", true);
    clock.advance(30 * sec);
    log.productStatusChanged(clock.nanoTime(), "<baz>", true);
    assertFormattedEvents(
        log,
        "<ul>",
        entry("19900101T000000Z", "19:00:00",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "Started"),
        entry("19900101T000003Z", "19:00:03",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "plan up to date : plan.js"),
        entry("19900101T000103Z", "19:01:03",
              "PT30S", "<span class=\"low-value\">00:</span>30",
              ("products up to date : "
               + "foo, <a href=\"products/bar.html\">bar</a>, &lt;baz&gt;")),
        "</ul>");
    clock.advance(30 * sec);
    log.productStatusChanged(clock.nanoTime(), "boo", true);
    log.productStatusChanged(clock.nanoTime(), "far", true);
    log.productStatusChanged(clock.nanoTime(), "faz", true);
    log.productStatusChanged(clock.nanoTime(), "bar", false);
    assertFormattedEvents(
        log,
        "<ul>",
        entry("19900101T000000Z", "19:00:00",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "Started"),
        entry("19900101T000003Z", "19:00:03",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "plan up to date : plan.js"),
        entry("19900101T000103Z", "19:01:03",
              "PT60S", "01:00",
              "6 products up to date"),
        entry("19900101T000203Z", "19:02:03",
              "PT0S", "<span class=\"low-value\">00:00</span>",
              "product invalid : <a href=\"products/bar.html\">bar</a>"),
        "</ul>");
  }

  private static String time(String className, String hcal, String pretty) {
    return "<abbr class=\"" + className + "\" title=\"" + hcal + "\">"
        + pretty + "</abbr>";
  }

  private static String dur(String className, String hcal, String pretty) {
    return "<abbr class=\"" + className + "\" title=\"" + hcal + "\">"
        + pretty + "</abbr>";
  }

  private static String entry(
      String startTimeHcal, String startTime,
      String durHcal, String dur,
      String text) {
    return Joiner.on("").join(
        "<li>",
        "<span class=\"time\">",
        time("dtstart", startTimeHcal, startTime),
        " + ",
        dur("duration", durHcal, dur),
        "</span>",
        text,
        "</li>\n");
  }

  /** Links the product bar and no other entities. */
  private static final class TestEntityLinker implements EntityLinker {
    public void endLink(String entityType, String entityName, Appendable out)
        throws IOException {
      assertEquals("bar", entityName);
      assertEquals("product", entityType);
      out.append("</a>");
    }

    public boolean linkEntity(
        String entityType, String entityName, Appendable out)
        throws IOException {
      if (!("bar".equals(entityName) && "product".equals(entityType))) {
        return false;
      }
      out.append("<a href=\"products/bar.html\">");
      return true;
    }
  }

  private void assertFormattedEvents(HighLevelLog log, String... golden) {
    TimeZone est = TimeZone.getTimeZone("EST");
    assertEquals(
        Joiner.on("").join(golden),
        log.formatEvents(log.snapshot(), new TestEntityLinker(), est).html()
            .replace("</li>", "</li>\n"));
  }
}
