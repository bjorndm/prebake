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
  public final @Nonnull PreformattedStaticHtml summaryHtml;
  /** A more detailed description. */
  public final @Nonnull PreformattedStaticHtml detailHtml;
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
    // Coercing HTML to balanced preformatted static HTML makes sure that the
    // summary HTML has balanced tags even when just a prefix of the details.
    this.summaryHtml = PreformattedStaticHtml.of(summaryHtml != null
        ? summaryHtml : summaryOf(detailHtml));
    this.detailHtml = PreformattedStaticHtml.of(detailHtml);
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
      return new Documentation(summary, detail, contact);
    }
    public String exampleText() { return MAP_CONV.exampleText(); }
  };

  /**
   * True if it can be rendered as just the detail string without losing data.
   */
  public boolean isDetailOnly() {
    return contactEmail == null && isSummaryInferred();
  }

  public boolean isSummaryInferred() {
    return summaryOf(detailHtml.plainText()).equals(summaryHtml.plainText());
  }

  public void toJson(JsonSink sink) throws IOException { toJson(sink, false); }

  public void toJson(JsonSink sink, boolean full) throws IOException {
    boolean skipSummary = !full && isSummaryInferred();
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
