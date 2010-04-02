package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;

import java.io.IOException;

/**
 * Structured documentation of a system artifact.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/DocumentationRecord">
 *   wiki</a>
 */
public final class Documentation implements JsonSerializable {
  public final String summaryHtml;
  public final String detailHtml;
  public final String contactEmail;

  /**
   * Implements Javadoc's summary string convention.
   * Per
   * <a href="http://java.sun.com/j2se/javadoc/writingdoccomments/">javadoc</a>
   * <blockquote>
   *     This sentence ends at the first period that is followed by a blank,
   *     tab, or line terminator, or at the first tag (as defined below).
   *     For example, this first sentence ends at "Prof.":
   *     <br>This is a simulation of Prof. Knuth's MIX computer.
   * </blockquote>
   */
  public static String summaryOf(String detailHtml) {
    int firstTag = detailHtml.indexOf('@');
    if (firstTag >= 0) {
      if (firstTag > 0 && detailHtml.charAt(firstTag - 1) == '{') {
        --firstTag;  // end before {@link ...}
      }
      detailHtml = detailHtml.substring(0, firstTag);
    }
    detailHtml = detailHtml.trim();
    for (int dot = -1, n = detailHtml.length();
         (dot = detailHtml.indexOf('.', dot + 1)) >= 0;) {
      if (dot + 1 == n || Character.isWhitespace(detailHtml.charAt(dot + 1))) {
        return detailHtml.substring(0, dot + 1);
      }
    }
    return detailHtml;
  }

  public Documentation(
      String summaryHtml, String detailHtml, String contactEmail) {
    this.summaryHtml = summaryHtml;
    this.detailHtml = detailHtml;
    this.contactEmail = contactEmail;

    // TODO: coerce HTML to balanced preformatted static HTML with normalized
    // entities.  The balanced tag part is critical for summaryHtml.
  }

  public Documentation(String helpHtml, String contactEmail) {
    this(summaryOf(helpHtml), helpHtml, contactEmail);
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("summary").write(":").writeValue(summaryHtml)
        .write(",").writeValue("detail").write(":").writeValue(detailHtml)
        .write(",").writeValue("contact").write(":").writeValue(contactEmail)
        .write("}");
  }
}
