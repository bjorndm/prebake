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

import org.prebake.html.DomFilter;
import org.prebake.html.TextChunk;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;

import java.io.IOException;
import java.io.StringReader;
import com.google.caja.lang.css.CssSchema;
import com.google.caja.lang.html.HtmlSchema;
import com.google.caja.lexer.HtmlTokenType;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.ParseException;
import com.google.caja.lexer.TokenQueue;
import com.google.caja.parser.html.DomParser;
import com.google.caja.parser.html.Nodes;
import com.google.caja.reporting.DevNullMessageQueue;
import com.google.gxp.base.GxpContext;
import com.google.gxp.html.HtmlClosure;

import org.w3c.dom.Node;

/**
 * A class that takes malformed HTML, removes sharp edges, normalizes it, and
 * provides a plain text alternative.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see <a href="http://code.google.com/p/prebake/wiki/PreformattedStaticHtml">
 *     wiki</a>
 */
public class PreformattedStaticHtml implements JsonSerializable, HtmlClosure {
  /** Input HTML that is lazily converted to HTML or plain text as need. */
  private final String src;
  private transient String html;
  private transient String plainText;

  /** Factory. */
  public static PreformattedStaticHtml of(String s) {
    return new PreformattedStaticHtml(s);
  }

  private PreformattedStaticHtml(String html) {
    this.src = html;
    for (int i = 0, n = html.length(); i < n; ++i) {
      switch (html.charAt(i)) {
        case '\r': case '<': case '>': case '&':
        case '\u0085': case '\u2028': case '\u2029':
          return;  // Don't set below.
      }
    }
    this.html = this.plainText = html;
  }

  /** A best effort at text formatted from the input HTML. */
  public String plainText() {
    if (plainText == null) { filter(); }
    return plainText;
  }

  /** Well-formed HTML with scripts and sharp edges removed. */
  public String html() {
    if (html == null) { filter(); }
    return html;
  }

  /**
   * Parses the source HTML, removes any unsafe constructs, and produces a
   * plain text form.
   */
  private void filter() {
    try {
      TokenQueue<HtmlTokenType> tq = DomParser.makeTokenQueue(
          InputSource.UNKNOWN, new StringReader(src), false);
      DomParser p = new DomParser(
          tq, /* as HTML */ false, DevNullMessageQueue.singleton());
      Node html = p.parseFragment();
      HtmlSchema htmlSchema = HtmlSchema.getDefault(
          DevNullMessageQueue.singleton());
      CssSchema cssSchema = CssSchema.getDefaultCss21Schema(
          DevNullMessageQueue.singleton());
      DomFilter f = new DomFilter(htmlSchema, cssSchema);
      TextChunk tc = f.filter(html);
      this.html = Nodes.render(html);
      // Produce the plain text form.
      StringBuilder plainText = new StringBuilder(this.html.length());
      tc.write(0, plainText);
      this.plainText = plainText.toString();

    // We don't fail fast here since the correctness of our HTML transformation
    // is not key, and any problems in documentation should be visible in a way
    // that build correctness issues are often not.
    } catch (IOException ex) {
      ex.printStackTrace();
      fallbackFilter();
    } catch (ParseException ex) {
      ex.printStackTrace();
      fallbackFilter();
    } catch (RuntimeException ex) {
      ex.printStackTrace();
      fallbackFilter();
    }
  }

  private void fallbackFilter() {
    // If the HTML parser fails for some reason, then just escape something.
    // This is ugly, but will keep the system ticking over.
    this.plainText = src;
    this.html = Nodes.encode(src);
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.writeValue(html());
  }

  public void write(Appendable out, GxpContext c) throws IOException {
    out.append(html());
  }
}
