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
        entry("19900101T000000Z", "1989-12-31 19:00:00",
              "19900101T000000Z", "1989-12-31 19:00:00",
              "Started"),
        "</ul>");
    clock.advance(3 * sec);
    log.planStatusChanged(true);
    clock.advance(60 * sec);
    log.productStatusChanged("foo", true);
    log.productStatusChanged("bar", true);
    clock.advance(60 * sec);
    log.productStatusChanged("<baz>", true);
    assertFormattedEvents(
        log,
        "<ul>",
        entry("19900101T000000Z", "1989-12-31 19:00:00",
              "19900101T000000Z", "1989-12-31 19:00:00",
              "Started"),
        entry("19900101T000003Z", "1989-12-31 19:00:03",
              "19900101T000003Z", "1989-12-31 19:00:03",
              "plan up to date : plan"),
        entry("19900101T000103Z", "1989-12-31 19:01:03",
              "19900101T000203Z", "1989-12-31 19:02:03",
              "products up to date : foo, bar, &lt;baz&gt;"),
        "</ul>");
    log.productStatusChanged("boo", true);
    log.productStatusChanged("far", true);
    log.productStatusChanged("faz", true);
    log.productStatusChanged("bar", false);
    assertFormattedEvents(
        log,
        "<ul>",
        entry("19900101T000000Z", "1989-12-31 19:00:00",
              "19900101T000000Z", "1989-12-31 19:00:00",
              "Started"),
        entry("19900101T000003Z", "1989-12-31 19:00:03",
              "19900101T000003Z", "1989-12-31 19:00:03",
              "plan up to date : plan"),
        entry("19900101T000103Z", "1989-12-31 19:01:03",
              "19900101T000203Z", "1989-12-31 19:02:03",
              "6 products up to date"),
        entry("19900101T000203Z", "1989-12-31 19:02:03",
              "19900101T000203Z", "1989-12-31 19:02:03",
              "product invalid : bar"),
        "</ul>");
  }

  private static String time(String className, String hcal, String pretty) {
    return "<abbr class=\"" + className + "\" title=\"" + hcal + "\">"
        + pretty + "</abbr>";
  }

  private static String entry(
      String startTimeHcal, String startTime,
      String endTimeHcal, String endTime,
      String text) {
    boolean sameTime = startTimeHcal.equals(endTimeHcal);
    return Joiner.on("").join(
        "<li>",
        "<span class=\"time\">",
        time("dtstart", startTimeHcal, startTime),
        sameTime ? "<span class=\"redundant-end-date\">" : "",
        " - ",
        time("dtend", endTimeHcal, endTime),
        sameTime ? "</span>" : "",
        "</span>",
        text,
        "</li>\n");
  }

  private void assertFormattedEvents(HighLevelLog log, String... golden) {
    TimeZone est = TimeZone.getTimeZone("EST");
    assertEquals(
        Joiner.on("").join(golden),
        log.formatEvents(log.snapshot(), est).html()
            .replace("</li>", "</li>\n"));
  }
}
