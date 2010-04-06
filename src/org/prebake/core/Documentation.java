package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;

import java.io.IOException;
import java.util.Map;

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

  public enum Field {
    summary,
    detail,
    contact,
    ;
  }

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
    // TODO: coerce HTML to balanced preformatted static HTML with normalized
    // entities.  The balanced tag part is critical for summaryHtml.
    this.summaryHtml = summaryHtml != null
        ? summaryHtml : summaryOf(detailHtml);
    this.detailHtml = detailHtml;
    this.contactEmail = contactEmail;
  }

  public Documentation(String helpHtml, String contactEmail) {
    this(null, helpHtml, contactEmail);
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  public static final YSONConverter<Documentation> CONVERTER
      = new YSONConverter<Documentation>() {
    final YSONConverter<String> strIdent = YSONConverter.Factory
        .withType(String.class);
    final YSONConverter<String> optStrIdent = YSONConverter.Factory
        .optional(strIdent);
    final YSONConverter<Map<Field, String>> MAP_CONV = YSONConverter.Factory
        .<Field, String>mapConverter(Field.class)
        .require("detail", strIdent)
        .optional("summary", optStrIdent, null)
        .optional("contact", optStrIdent, null)
        .build();
    public Documentation convert(Object ysonValue, MessageQueue problems) {
      String summary, detail, contact;
      if (ysonValue instanceof String) {
        detail = (String) ysonValue;
        summary = contact = null;
      } else {
        Map<Field, String> pairs = MAP_CONV.convert(ysonValue, problems);
        if (pairs == null) { return null; }
        summary = pairs.get(Field.summary);
        detail = pairs.get(Field.detail);
        contact = pairs.get(Field.contact);
        if (detail == null) { return null; }  // message logged by MAP_CONV
      }
      return new Documentation(summary, detail, contact);
    }
    public String exampleText() { return MAP_CONV.exampleText(); }
  };

  /**
   * True if it can be rendered as just the detail string without losing data.
   */
  public boolean isDetailOnly() {
    return contactEmail == null && summaryOf(detailHtml).equals(summaryHtml);
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("summary").write(":").writeValue(summaryHtml)
        .write(",").writeValue("detail").write(":").writeValue(detailHtml)
        .write(",").writeValue("contact").write(":").writeValue(contactEmail)
        .write("}");
  }
}
