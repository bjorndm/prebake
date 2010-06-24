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

package org.prebake.html;

import java.util.List;
import java.util.Locale;

import com.google.caja.lang.css.CssSchema;
import com.google.caja.lang.html.HTML;
import com.google.caja.lang.html.HtmlSchema;
import com.google.caja.lexer.CharProducer;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.ParseException;
import com.google.caja.lexer.TokenConsumer;
import com.google.caja.parser.AncestorChain;
import com.google.caja.parser.css.CssParser;
import com.google.caja.parser.css.CssTree;
import com.google.caja.parser.html.AttribKey;
import com.google.caja.parser.html.ElKey;
import com.google.caja.parser.html.Namespaces;
import com.google.caja.parser.html.Nodes;
import com.google.caja.plugin.CssRewriter;
import com.google.caja.plugin.CssValidator;
import com.google.caja.plugin.UriPolicy;
import com.google.caja.reporting.DevNullMessageQueue;
import com.google.caja.reporting.MessageLevel;
import com.google.caja.reporting.MessageQueue;
import com.google.caja.reporting.RenderContext;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Recursively walks a DOM tree, removing unsafe bits.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class DomFilter {
  private final HtmlSchema htmlSchema;
  private final CssSchema cssSchema;

  public DomFilter(HtmlSchema htmlSchema, CssSchema cssSchema) {
    this.htmlSchema = htmlSchema;
    this.cssSchema = cssSchema;
  }

  public TextChunk filter(Node n) {
    // TODO: figure out how to do tab expansion of the plain text mode.
    switch (n.getNodeType()) {
      case Node.ELEMENT_NODE:
        Element el = (Element) n;
        ElKey elkey = ElKey.forElement(el);
        if (!(htmlSchema.isElementAllowed(elkey)
              || (elkey.isHtml() && WHITELIST.contains(elkey.localName)))) {
          n.getParentNode().removeChild(n);
          return null;
        }
        for (Attr a : Lists.newArrayList(Nodes.attributesOf(el))) {
          AttribKey akey = AttribKey.forAttribute(elkey, a);
          if (!htmlSchema.isAttributeAllowed(akey)) {
            el.removeAttributeNode(a);
          } else {
            String value = a.getValue();
            HTML.Attribute ainfo = htmlSchema.lookupAttribute(akey);
            HTML.Attribute.Type t = ainfo != null ? ainfo.getType() : null;
            if (ainfo == null || !ainfo.getValueCriterion().accept(value)
                // TODO: white-list, and use caja's static HTML checker instead.
                || t == HTML.Attribute.Type.SCRIPT) {
              el.removeAttributeNode(a);
            } else if (t == HTML.Attribute.Type.STYLE) {
              String css = a.getValue();
              String sanitizedCss = null;
              try {
                MessageQueue mq = DevNullMessageQueue.singleton();
                CharProducer cssP = CharProducer.Factory.fromString(
                    css, InputSource.UNKNOWN);
                CssParser cssp = new CssParser(
                    CssParser.makeTokenQueue(cssP, mq, false),
                    mq, MessageLevel.WARNING);
                CssTree.DeclarationGroup decls = cssp.parseDeclarationGroup();
                CssValidator v = new CssValidator(cssSchema, htmlSchema, mq);
                CssRewriter rw = new CssRewriter(
                    UriPolicy.IDENTITY, cssSchema, mq);
                AncestorChain<CssTree.DeclarationGroup> ac
                    = AncestorChain.instance(decls);
                v.validateCss(ac);
                rw.rewrite(ac);
                StringBuilder sb = new StringBuilder();
                TokenConsumer tc = decls.makeRenderer(sb, null);
                decls.render(new RenderContext(tc));
                tc.noMoreTokens();
                if (sb.length() != 0) { sanitizedCss = sb.toString(); }
              } catch (ParseException ex) {
                // Remove below.
              }
              if (sanitizedCss == null) {
                el.removeAttributeNode(a);
              } else {
                a.setValue(sanitizedCss);
              }
            }
          }
        }
        HTML.Element info = htmlSchema.lookupElement(elkey);
        if (info != null && info.isEmpty()) {
          for (Node child; (child = n.getFirstChild()) != null;) {
            n.removeChild(child);
          }
          if ("br".equals(elkey.localName)) {
            return new Break();
          } else if ("img".equals(elkey.localName)) {
            String alt = el.getAttributeNS(
                Namespaces.HTML_NAMESPACE_URI, "alt");
            return alt == null || alt.length() == 0
                ? null : new SimpleTextChunk("[" + alt + "]");
          } else {
            return null;
          }
        } else {
          List<TextChunk> chunks = filterRecurse(n);
          if (isBlock(elkey)) {
            return TextBlock.make(chunks);
          } else if ("table".equals(elkey.localName)) {
            return new Table(chunks);
          } else if ("td".equals(elkey.localName)
                     || "th".equals(elkey.localName)) {
            TextChunk body = InlineText.make(chunks);
            if ("th".equals(elkey.localName)) { body = new CenteredText(body); }
            int colspan = 1;
            String colspanStr = el.getAttributeNS(
                Namespaces.HTML_NAMESPACE_URI, "colspan");
            if (colspanStr != null) {
              try {
                colspan = Integer.parseInt(colspanStr, 10);
                if (colspan < 1) { colspan = 1; }
              } catch (NumberFormatException ex) {
                // ignore
              }
            }
            String rowspanStr = el.getAttributeNS(
                Namespaces.HTML_NAMESPACE_URI, "rowspan");
            int rowspan = 1;
            if (rowspanStr != null) {
              try {
                rowspan = Integer.parseInt(rowspanStr, 10);
                if (rowspan < 1) { rowspan = 1; }
              } catch (NumberFormatException ex) {
                // ignore
              }
            }
            // Make sure table cells are not aggregated into InlineText by
            // creating a separate wrapper node.
            return new TableCell(body, colspan, rowspan);
          } else if ("ul".equals(elkey.localName)
                     || "ol".equals(elkey.localName)) {
            String bulletType = el.getAttributeNS(
                Namespaces.HTML_NAMESPACE_URI, "type");
            if (bulletType == null || bulletType.length() != 1) {
              bulletType = "ol".equals(elkey.localName) ? "1" : "#";
            }
            Function<Integer, String> bulletMaker;
            switch (bulletType.charAt(0)) {
              case '1':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) { return "" + (i + 1); }
                };
                break;
              case 'a':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) {
                    return String.valueOf((char) ('a' + (i % 26)));
                  }
                };
                break;
              case 'A':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) {
                    return String.valueOf((char) ('a' + (i % 26)));
                  }
                };
                break;
              case 'I':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) {
                    return TextUtil.toRomanNumeral(i + 1);
                  }
                };
                break;
              case 'i':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) {
                    return TextUtil.toRomanNumeral(i + 1)
                        .toLowerCase(Locale.ROOT);
                  }
                };
                break;
              default:
                final String bullet = bulletType;
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) { return bullet; }
                };
            }
            return new ListBlock(chunks, bulletMaker);
          } else if ("blockquote".equals(elkey.localName)) {
            return new ListBlock(chunks, new Function<Integer, String>() {
              public String apply(Integer i) { return " "; }
            });
          } else if ("center".equals(elkey.localName)) {
            return new CenteredText(InlineText.make(chunks));
          } else {
            return InlineText.make(chunks);
          }
        }
      case Node.TEXT_NODE:
        return new SimpleTextChunk(n.getNodeValue());
      case Node.DOCUMENT_FRAGMENT_NODE:
        return TextBlock.make(filterRecurse(n));
      default:
        n.getParentNode().removeChild(n);
        return null;
    }
  }

  ImmutableList<TextChunk> filterRecurse(Node n) {
    ImmutableList.Builder<TextChunk> chunks = ImmutableList.builder();
    Node child = n.getFirstChild();
    while (child != null) {
      Node prior = child.getPreviousSibling();
      TextChunk chunk = filter(child);
      if (chunk != null) { chunks.add(chunk); }
      if (child.getParentNode() == n) {
        child = child.getNextSibling();
      } else if (prior == null) {
        child = n.getFirstChild();
      } else {
        child = prior.getNextSibling();
      }
    }
    return chunks.build();
  }

  /** Set of extra allowed elements that are not defined in HTML4. */
  private static final ImmutableSet<String> WHITELIST = ImmutableSet.of(
      "xmp", "plaintext");

  /** Names of HTML elements that start and end blocks. */
  private static final ImmutableSet<String> BLOCK_NAMES = ImmutableSet.of(
      "pre", "p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "li", "tr",
      "option", "caption", "tbody", "thead", "tfoot", "xmp");

  private static boolean isBlock(ElKey k) {
    return k.isHtml() && BLOCK_NAMES.contains(k.localName);
  }
}
