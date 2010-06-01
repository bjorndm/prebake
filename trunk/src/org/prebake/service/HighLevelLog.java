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

import org.prebake.core.PreformattedStaticHtml;
import org.prebake.util.Clock;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.Set;
import java.util.TimeZone;
import com.google.caja.lexer.escaping.Escaping;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A high level log that allows for a compact view of recent activity.
 * Usually this log is viewed by the {@link org.prebake.service.www web} view.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class HighLevelLog {
  private final LinkedList<HighLevelEvent> recentItems = Lists.newLinkedList();
  private final Clock clock;

  private static final int MAX_RECENT_ITEM_COUNT = 50;

  public HighLevelLog(Clock clock) {
    this.clock = clock;
    recentItems.add(new SystemStartupEvent(clock.nanoTime()));
  }

  public void productStatusChanged(String productName, boolean upToDate) {
    enqueue(new StatusChangedEvent(
        clock.nanoTime(), upToDate, "product", productName));
  }

  public void toolStatusChanged(String toolName, boolean upToDate) {
    enqueue(new StatusChangedEvent(
        clock.nanoTime(), upToDate, "tool", toolName));
  }

  public void planStatusChanged(boolean upToDate) {
    enqueue(new StatusChangedEvent(
        clock.nanoTime(), upToDate, "plan", "plan"));
  }

  private void enqueue(HighLevelEvent e) {
    synchronized (recentItems) {
      HighLevelEvent last = recentItems.getLast();
      if (e.foldInto(last)) { return; }
      if (recentItems.size() > MAX_RECENT_ITEM_COUNT) {
        recentItems.removeFirst();
      }
      recentItems.add(e);
    }
  }

  public ImmutableList<HighLevelEvent> snapshot() {
    return snapshot(MAX_RECENT_ITEM_COUNT);
  }

  public ImmutableList<HighLevelEvent> snapshot(int max) {
    synchronized (recentItems) {
      int n = recentItems.size();
      return ImmutableList.copyOf(
          n > max ? recentItems.subList(n - max, n) : recentItems);
    }
  }

  public PreformattedStaticHtml formatEvents(
      Iterable<? extends HighLevelEvent> events, TimeZone tz) {
    StringBuilder html = new StringBuilder();
    html.append("<ul>");
    StringBuilder plainText = new StringBuilder();
    for (HighLevelEvent e : events) {
      html.append("<li><span class=\"time\">");
      long t0 = e.t0;
      long t1 = e.t0 + e.duration;
      formatDate(t0, true, tz, html);
      if (t1 == t0) { html.append("<span class=\"redundant-end-date\">"); }
      html.append(" - ");
      formatDate(t1, false, tz, html);
      if (t1 == t0) { html.append("</span>"); }  // end redundant-end-date
      html.append("</span>");  // end time
      plainText.setLength(0);
      try {
        e.format(plainText);
      } catch (IOException ex) {
        Throwables.propagate(ex);
      }
      Escaping.escapeXml(plainText, false, html);
      html.append("</li>");
    }
    html.append("</ul>");
    return PreformattedStaticHtml.of(html.toString());
  }

  private void formatDate(
      long nanoTime, boolean start, TimeZone tz, StringBuilder out) {
    String hcal, pretty;
    {
      Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      clock.toCalendar(nanoTime, c);

      DateFormat hcalendarFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
      hcalendarFormat.setCalendar(c);
      hcal = hcalendarFormat.format(c.getTimeInMillis());

      // Switch to the user visible timezone.
      c.setTimeZone(tz);
      DateFormat prettyFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      prettyFormat.setCalendar(c);
      pretty = prettyFormat.format(c.getTimeInMillis());
    }

    out.append("<abbr class=\"").append(start ? "dtstart" : "dtend")
        .append("\" title=\"")
    // It's easier for a lot of hcalendar clients if all dates are in UTC.
        .append(hcal).append("\">").append(pretty).append("</abbr>");
  }

  static abstract class HighLevelEvent {
    protected final long t0;
    protected long duration;

    HighLevelEvent(long t0) { this.t0 = t0; }

    /**
     * True if this event could be incorporated into e so is no longer needed.
     * @param e an event whose t0 is at or before this event's t0.
     */
    abstract boolean foldInto(HighLevelEvent e);
    abstract void format(Appendable out) throws IOException;
  }
}

final class SystemStartupEvent extends HighLevelLog.HighLevelEvent {
  SystemStartupEvent(long t0) { super(t0); }

  @Override
  boolean foldInto(HighLevelLog.HighLevelEvent e) { return false; }

  @Override
  void format(Appendable out) throws IOException { out.append("Started"); }
}

final class StatusChangedEvent extends HighLevelLog.HighLevelEvent {
  final boolean upToDate;
  final String artifactType;
  final Set<String> artifacts = Sets.newLinkedHashSet();

  /**
   * @param artifactType a human readable string like
   *     {@code "tool"} or {@code "product"} that can be pluralized by appending
   *     an {@code 's'}.
   */
  StatusChangedEvent(
      long t0, boolean upToDate, String artifactType, String artifactName) {
    super(t0);
    this.upToDate = upToDate;
    this.artifactType = artifactType;
    this.artifacts.add(artifactName);
  }

  @Override
  public boolean foldInto(HighLevelLog.HighLevelEvent e) {
    if (e.getClass() != this.getClass()) { return false; }
    StatusChangedEvent that = (StatusChangedEvent) e;
    if (this.upToDate != that.upToDate) { return false; }
    if (!this.artifactType.equals(that.artifactType)) { return false; }
    that.artifacts.addAll(this.artifacts);
    that.duration = Math.max(that.duration, this.t0 + this.duration - that.t0);
    return true;
  }

  @Override
  public void format(Appendable out) throws IOException {
    int nArtifacts = artifacts.size();
    if (nArtifacts > 5) {
      out.append(Integer.toString(nArtifacts)).append(' ');
      formatMessage(nArtifacts, out);
    } else {
      formatMessage(nArtifacts, out);
      out.append(" : ");
      Joiner.on(", ").appendTo(out, artifacts);
    }
  }

  private void formatMessage(int n, Appendable out) throws IOException {
    out.append(artifactType);
    if (n != 1) { out.append('s'); }
    out.append(upToDate ? " up to date" : " invalid");
  }
}
