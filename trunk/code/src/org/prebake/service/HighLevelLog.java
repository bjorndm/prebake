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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TimeZone;

import com.google.caja.lexer.escaping.Escaping;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A high level log that allows for a compact view of recent activity.
 * Usually this log is viewed via the
 * {@link org.prebake.service.www.MainServlet web} view.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class HighLevelLog {
  private final LinkedList<HighLevelEvent> recentItems = Lists.newLinkedList();
  private final Clock clock;

  private static final int MAX_RECENT_ITEM_COUNT = 50;

  public HighLevelLog(Clock clock) {
    this.clock = clock;
    // Make sure there is always a last item on the list to simplify enqueue.
    recentItems.add(new SystemStartupEvent(clock.nanoTime()));
  }

  public Clock getClock() { return clock; }

  /**
   * Log the fact that a product's status has changed.
   * @param t0 a timestamp relative to this log's {@link #getClock clock}.
   */
  public void productStatusChanged(
      long t0, String productName, boolean upToDate) {
    enqueue(new StatusChangedEvent(
        t0, clock.nanoTime(), upToDate, "product", productName));
  }

  /**
   * Log the fact that a tool's status has changed.
   * @param t0 a timestamp relative to this log's {@link #getClock clock}.
   */
  public void toolStatusChanged(long t0, String toolName, boolean upToDate) {
    enqueue(new StatusChangedEvent(
        t0, clock.nanoTime(), upToDate, "tool", toolName));
  }

  /**
   * Log the fact that a plan's status has changed.
   * @param t0 a timestamp relative to this log's {@link #getClock clock}.
   */
  public void planStatusChanged(long t0, String planFile, boolean upToDate) {
    enqueue(new StatusChangedEvent(
        t0, clock.nanoTime(), upToDate, "plan", planFile));
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

  private static final long NANOSECS_PER_SEC = 1000 * 1000 * 1000;

  public PreformattedStaticHtml formatEvents(
      Iterable<? extends HighLevelEvent> events, EntityLinker el, TimeZone tz) {
    StringBuilder html = new StringBuilder();
    html.append("<ul>");
    for (HighLevelEvent e : events) {
      html.append("<li><span class=\"time\">");
      long t0 = e.t0;
      formatDate(t0, true, tz, html);
      html.append(" + ");
      formatDuration(e.duration, html);
      html.append("</span>");  // end time
      try {
        e.formatHtml(el, html);
      } catch (IOException ex) {
        Throwables.propagate(ex);
      }
      html.append("</li>");
    }
    html.append("</ul>");
    return PreformattedStaticHtml.of(html.toString());
  }

  private void formatDate(
      long nanoTime, boolean start, TimeZone tz, StringBuilder out) {
    String hcal, pretty;
    {
      // It's easier for a lot of hcalendar clients if all dates are in UTC.
      Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      clock.toCalendar(nanoTime, c);

      DateFormat hcalendarFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
      hcalendarFormat.setCalendar(c);
      hcal = hcalendarFormat.format(c.getTimeInMillis());

      // Switch to the user visible timezone.
      c.setTimeZone(tz);
      DateFormat prettyFormat = new SimpleDateFormat(
          /*"yyyy-MM-dd " + */ "HH:mm:ss");
      prettyFormat.setCalendar(c);
      pretty = prettyFormat.format(c.getTimeInMillis());
    }

    out.append("<abbr class=\"").append(start ? "dtstart" : "dtend")
        .append("\" title=\"")
        .append(hcal).append("\">").append(pretty).append("</abbr>");
  }

  private void formatDuration(long nanos, StringBuilder out) {
    long seconds = nanos / NANOSECS_PER_SEC;
    int sign = 1;
    if (seconds < 0) {
      sign = -1;
      seconds *= -1;
    }
    String hcalDur = (sign < 0 ? "-" : "") + "PT" + seconds + "S";
    out.append("<abbr class=\"duration\" title=\"").append(hcalDur)
        .append("\">");

    long minutes = seconds / 60;
    seconds -= minutes * 60;
    //long hours = minutes / 60;
    //minutes -= hours * 60;

    boolean lowValueInfoOpen = minutes == 0;
    if (lowValueInfoOpen) { out.append("<span class=\"low-value\">"); }
    //lowValueInfoOpen = durationDigits(hours, false, lowValueInfoOpen, out);
    lowValueInfoOpen = durationDigits(minutes, false, lowValueInfoOpen, out);
    lowValueInfoOpen = durationDigits(seconds, true, lowValueInfoOpen, out);
    if (lowValueInfoOpen) { out.append("</span>"); }
  }

  private boolean durationDigits(
      long n, boolean colon, boolean lowValueInfoOpen, StringBuilder out) {
    if (colon) { out.append(':'); }
    if (n != 0 && lowValueInfoOpen) {
      out.append("</span>");
      lowValueInfoOpen = false;
    }
    if (n < 10) { out.append('0'); }
    out.append(Long.toString(n));
    return lowValueInfoOpen;
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
    /**
     * @param el specifies how to link product names and tool names.
     * @param out channel to which HTML output is written.
     * @throws IOException if out raises an IOException.
     */
    abstract void formatHtml(EntityLinker el, Appendable out)
        throws IOException;
  }
}

final class SystemStartupEvent extends HighLevelLog.HighLevelEvent {
  SystemStartupEvent(long t0) { super(t0); }

  @Override
  boolean foldInto(HighLevelLog.HighLevelEvent e) { return false; }

  @Override
  void formatHtml(EntityLinker el, Appendable out) throws IOException {
    out.append("Started");
  }
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
      long t0, long t1, boolean upToDate, String artifactType,
      String artifactName) {
    super(t0);
    assert t1 >= t0;
    this.duration = t1 - t0;
    this.upToDate = upToDate;
    this.artifactType = artifactType;
    this.artifacts.add(artifactName);
  }

  @Override
  boolean foldInto(HighLevelLog.HighLevelEvent e) {
    if (e.getClass() != this.getClass()) { return false; }
    StatusChangedEvent that = (StatusChangedEvent) e;
    if (this.upToDate != that.upToDate) { return false; }
    if (!this.artifactType.equals(that.artifactType)) { return false; }
    that.artifacts.addAll(this.artifacts);
    that.duration = Math.max(that.duration, this.t0 + this.duration - that.t0);
    return true;
  }

  @Override
  void formatHtml(EntityLinker el, Appendable out)
      throws IOException {
    int nArtifacts = artifacts.size();
    if (nArtifacts > 5) {
      out.append(Integer.toString(nArtifacts)).append(' ');
      writeMessage(nArtifacts, el, out);
    } else {
      writeMessage(nArtifacts, el, out);
      String sep = " : ";
      for (Iterator<String> it = artifacts.iterator(); it.hasNext();) {
        out.append(sep);
        sep = ", ";

        String artifactName = it.next();
        boolean endLink = el.linkEntity(artifactType, artifactName, out);
        Escaping.escapeXml(artifactName, false, out);
        if (endLink) { el.endLink(artifactType, artifactName, out); }
      }
    }
  }

  private void writeMessage(int n, EntityLinker el, Appendable out)
      throws IOException {
    boolean endLink = el.linkEntity(artifactType, null, out);
    out.append(artifactType);
    if (n != 1) { out.append('s'); }
    if (endLink) { el.endLink(artifactType, null, out); }
    out.append(upToDate ? " up to date" : " invalid");
  }
}
