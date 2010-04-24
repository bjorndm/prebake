package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Structured documentation of a system artifact.
 *
 * <p>When {@link Documentation#CONVERTER converted} from JSON, the
 * {@link Documentation#summaryHtml summary} is inferred by looking at the
 * first sentence.  This class uses {@link Documentation#summaryOf JavaDoc}
 * conventions for finding the first sentence instead of trying to segment human
 * languages.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/DocumentationRecord">
 *   wiki</a>
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Documentation implements JsonSerializable {
  /** A short summary, usually in one sentence. */
  public final @Nonnull String summaryHtml;
  /** A more detailed description. */
  public final @Nonnull String detailHtml;
  /**
   * The name and email address of the artifact's maintainer, ideally as a
   * mailbox production per RFC 2822, e.g. {@code John Doe <john.doe@host.tld>}.
   */
  public final @Nullable String contactEmail;

  /** Correspond to JavaScript property names in the JSON form. */
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
      @Nullable String summaryHtml, String detailHtml,
      @Nullable String contactEmail) {
    // TODO: coerce HTML to balanced preformatted static HTML with normalized
    // entities.  The balanced tag part is critical for summaryHtml.
    this.summaryHtml = summaryHtml != null
        ? summaryHtml : summaryOf(detailHtml);
    this.detailHtml = detailHtml;
    this.contactEmail = contactEmail;
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Documentation)) { return false; }
    Documentation that = (Documentation) o;
    return this.summaryHtml.equals(that.summaryHtml)
        && (this.contactEmail == null
            ? that.contactEmail == null
            : this.contactEmail.equals(that.contactEmail))
        && this.detailHtml.equals(that.detailHtml);
  }

  @Override
  public int hashCode() {
    return detailHtml.hashCode()
        + 31 * (summaryHtml.hashCode()
                + 31 * (contactEmail != null ? contactEmail.hashCode() : 0));
  }

  /** Coerces a JSON string or object to documentation. */
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
    public Documentation convert(
        @Nullable Object ysonValue, MessageQueue problems) {
      @Nullable String summary;
      @Nonnull String detail;
      @Nullable String contact;
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
      return new Documentation(summary, detail.toString(), contact);
    }
    public String exampleText() { return MAP_CONV.exampleText(); }
  };

  /**
   * True if it can be rendered as just the detail string without losing data.
   */
  public boolean isDetailOnly() {
    return contactEmail == null && summaryOf(detailHtml).equals(summaryHtml);
  }

  public void toJson(JsonSink sink) throws IOException { toJson(sink, false); }

  public void toJson(JsonSink sink, boolean full) throws IOException {
    boolean skipSummary = !full && summaryOf(detailHtml).equals(summaryHtml);
    if (skipSummary && contactEmail == null) {
      sink.writeValue(detailHtml);
      return;
    }
    sink.write("{");
    if (!skipSummary) {
      sink.writeValue("summary").write(":").writeValue(summaryHtml).write(",");
    }
    sink.writeValue("detail").write(":").writeValue(detailHtml);
    if (full || contactEmail != null) {
      sink.write(",").writeValue("contact").write(":").writeValue(contactEmail);
    }
    sink.write("}");
  }
}
