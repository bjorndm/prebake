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

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

import com.google.caja.lang.html.HTML;
import com.google.caja.lang.html.HtmlSchema;
import com.google.caja.lexer.HtmlTokenType;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.ParseException;
import com.google.caja.lexer.TokenQueue;
import com.google.caja.parser.html.AttribKey;
import com.google.caja.parser.html.DomParser;
import com.google.caja.parser.html.ElKey;
import com.google.caja.parser.html.Namespaces;
import com.google.caja.parser.html.Nodes;
import com.google.caja.reporting.DevNullMessageQueue;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A class that takes malformed HTML, removes sharp edges, normalizes it, and
 * provides a plain text alternative.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see <a href="http://code.google.com/p/prebake/wiki/PreformattedStaticHtml">
 *     wiki</a>
 */
public class PreformattedStaticHtml implements JsonSerializable {
  /** Input HTML that is lazily converted to HTML or plain text as need. */
  private final String src;
  private transient String html;
  private transient String plainText;

  // TODO: refactor this to separate out all the inner classes with long bodies.

  /** Factory. */
  public static PreformattedStaticHtml of(String s) {
    return new PreformattedStaticHtml(s);
  }

  private PreformattedStaticHtml(String html) {
    this.src = html;
    for (int i = 0, n = html.length(); i < n; ++i) {
      switch (html.charAt(i)) {
        case '<': case '>': case '&': return;  // Don't set below.
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
      HtmlSchema schema = HtmlSchema.getDefault(
          DevNullMessageQueue.singleton());
      DomFilter f = new DomFilter(schema);
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

  private static final char[] SIXTEEN_SPACES = new char[16];
  static { Arrays.fill(SIXTEEN_SPACES, ' '); }
  /** Adds nSpaces spaces to sb. */
  static void pad(StringBuilder sb, int nSpaces) {
    while (nSpaces >= 16) {
      sb.append(SIXTEEN_SPACES, 0, 16);
      nSpaces -= 16;
    }
    sb.append(SIXTEEN_SPACES, 0, nSpaces);
  }

  /**
   * Splits around UNIX, Windows, and old mac style newlines returning the empty
   * array for the empty string.
   */
  static String[] splitLines(CharSequence s) {
    if (s.length() == 0) { return new String[0]; }
    return s.toString().split("\r\n?|\n");
  }

  /** Renders an integer as an uppercase roman numeral. */
  static String toRomanNumeral(int n) {
    // Uses the algorithm described at
    // http://turner.faculty.swau.edu/mathematics/materialslibrary/roman/
    StringBuilder sb = new StringBuilder();
    while (n >= 1000) { sb.append('M'); n -= 1000; }
    while (n >= 500) { sb.append('D'); n -= 500; }
    while (n >= 100) { sb.append('C'); n -= 100; }
    while (n >= 50) { sb.append('L'); n -= 50; }
    while (n >= 10) { sb.append('X'); n -= 10; }
    while (n >= 5) { sb.append('V'); n -= 5; }
    while (n >= 1) { sb.append('I'); n -= 1; }
    for (int i = 0; i + 3 < sb.length(); ++i) {
      char ch = sb.charAt(i);
      if (ch == sb.charAt(i + 1)
          && ch == sb.charAt(i + 2)
          && ch == sb.charAt(i + 3)) {
        char left = i > 0 ? sb.charAt(i - 1) : '\0';
        switch (ch) {
          case 'I':
            if (left == 'V') {
              sb.replace(i - 1, i + 4, "IX");
            } else {
              sb.replace(i, i + 4, "IV");
            }
            break;
          case 'X':
            if (left == 'L') {
              sb.replace(i - 1, i + 4, "XC");
            } else {
              sb.replace(i, i + 4, "XL");
            }
            break;
          case 'C':
            if (left == 'D') {
              sb.replace(i - 1, i + 4, "CM");
            } else {
              sb.replace(i, i + 4, "CD");
            }
            break;
        }
      }
    }
    return sb.toString();
  }
}

/**
 * Recursively walks a DOM tree, removing unsafe bits.
 */
final class DomFilter {
  final HtmlSchema htmlSchema;
  DomFilter(HtmlSchema htmlSchema) {
    this.htmlSchema = htmlSchema;
  }

  TextChunk filter(Node n) {
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
            if (ainfo == null || !ainfo.getValueCriterion().accept(value)
                // TODO: white-list, and use caja's static HTML checker instead.
                || ainfo.getType() == HTML.Attribute.Type.SCRIPT
                || ainfo.getType() == HTML.Attribute.Type.STYLE) {
              el.removeAttributeNode(a);
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
                    return PreformattedStaticHtml.toRomanNumeral(i + 1);
                  }
                };
                break;
              case 'i':
                bulletMaker = new Function<Integer, String>() {
                  public String apply(Integer i) {
                    return PreformattedStaticHtml.toRomanNumeral(i + 1)
                        .toLowerCase(Locale.ENGLISH);
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

/** Represents a chunk of HTML along with sizing and positioning info. */
interface TextChunk {
  /** The width of the plain text form. */
  int width();
  /** The number of lines in the plain text form. */
  int height();
  /**
   * Appends the plain text form to the output buffer.
   * @param containerWidth a hint at the width of the container in case the
   *    chunk wishes to center or right justify itself.
   *    This hint might be wrong on the low side but will not be wrong on the
   *    high side, so the chunk can always assume it has at least as much
   *    space as containerWidth.
   */
  void write(int containerWidth, StringBuilder out);
  /** True if the next chunk following this chunk should fall on a new line. */
  boolean breaks();
}

/** Represents a line break. */
class Break implements TextChunk {
  public boolean breaks() { return true; }
  public int height() { return 0; }
  public void write(int containerWidth, StringBuilder sb) { /* do nothing. */ }
  public int width() { return 0; }
}

/** A chunk of text, that might span multiple lines. */
class SimpleTextChunk implements TextChunk {
  final String s;
  final int height;
  final int width;

  SimpleTextChunk(String s) {
    int height = 1;
    int width = 0;
    int start = 0;
    int n = s.length();
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      if (ch == '\n' || ch == '\r') {
        width = Math.max(width, i - start);
        ++height;
        if (ch == '\r' && i + 1 < n && s.charAt(i + 1) == '\n') { ++i; }
        start = i + 1;
      }
    }
    width = Math.max(n - start, width);
    this.s = s;
    this.height = height;
    this.width = width;
  }

  public boolean breaks() { return false; }
  public int height() { return height; }
  public void write(int containerWidth, StringBuilder sb) { sb.append(s); }
  public int width() { return width; }
}

/**
 * A series of chunks laid out horizontally.
 */
final class InlineText implements TextChunk {
  final ImmutableList<TextChunk> parts;
  final int height;
  final int width;

  static TextChunk make(List<TextChunk> chunks) {
    if (chunks.isEmpty()) { return new SimpleTextChunk(""); }
    if (chunks.size() == 1 && !chunks.get(0).breaks()) { return chunks.get(0); }
    ImmutableList.Builder<TextChunk> parts = ImmutableList.builder();
    for (TextChunk c : chunks) {
      if (c instanceof InlineText) {
        parts.addAll(((InlineText) c).parts);
      } else {
        parts.add(c);
      }
    }
    return new InlineText(parts.build());
  }

  private InlineText(List<TextChunk> parts) {
    this.parts = ImmutableList.copyOf(parts);
    int width = 0;
    int height = 0;
    for (TextChunk chunk : parts) {
      width += chunk.width();
      height = Math.max(height, chunk.height());
    }
    this.width = width;
    this.height = height;
  }

  public boolean breaks() { return false; }

  public int height() { return height; }

  public void write(int containerWidth, StringBuilder out) {
    StringBuilder[] lines = new StringBuilder[height];
    for (int i = lines.length; --i >= 0;) { lines[i] = new StringBuilder(); }
    StringBuilder sb = new StringBuilder();
    for (TextChunk part : parts) {
      sb.setLength(0);
      part.write(0, sb);
      String[] partLines = PreformattedStaticHtml.splitLines(sb);
      int partWidth = part.width();
      for (int j = 0; j < height; ++j) {
        int toFill = partWidth;
        if (j < partLines.length) {
          lines[j].append(partLines[j]);
          toFill -= partLines[j].length();
        }
        PreformattedStaticHtml.pad(lines[j], toFill);
      }
    }
    for (int i = 0; i < height; ++i) {
      if (i != 0) { out.append('\n'); }
      out.append(lines[i]);
    }
  }

  public int width() { return width; }
}

/**
 * A series of chunks laid out vertically.
 */
final class TextBlock implements TextChunk {
  final ImmutableList<TextChunk> parts;
  final int height;
  final int width;

  static TextChunk make(List<TextChunk> chunks) {
    if (chunks.isEmpty()) { return new SimpleTextChunk(""); }
    if (chunks.size() == 1 && chunks.get(0).breaks()) { return chunks.get(0); }
    ImmutableList.Builder<TextChunk> parts = ImmutableList.builder();
    List<TextChunk> inlineParts = Lists.newArrayList();
    for (TextChunk c : chunks) {
      if (!c.breaks()) {
        inlineParts.add(c);
      } else {
        switch (inlineParts.size()) {
          case 0: break;
          case 1: parts.add(inlineParts.get(0)); break;
          default: parts.add(InlineText.make(inlineParts)); break;
        }
        inlineParts.clear();
        if (c instanceof TextBlock) {
          parts.addAll(((TextBlock) c).parts);
        } else {
          parts.add(c);
        }
      }
    }
    switch (inlineParts.size()) {
      case 0: break;
      case 1: parts.add(inlineParts.get(0)); break;
      default: parts.add(InlineText.make(inlineParts)); break;
    }
    return new TextBlock(parts.build());
  }

  private TextBlock(List<TextChunk> parts) {
    this.parts = ImmutableList.copyOf(parts);
    int width = 0;
    int height = 0;
    for (TextChunk chunk : parts) {
      height += chunk.height();
      width = Math.max(width, chunk.width());
    }
    this.width = width;
    this.height = height;
  }

  public boolean breaks() { return true; }

  public int height() { return height; }

  public void write(int containerWidth, StringBuilder out) {
    int pos = out.length();
    for (int i = 0, n = parts.size(); i < n; ++i) {
      if (pos != out.length()) {
        out.append('\n');
        pos = out.length();
      }
      parts.get(i).write(Math.max(containerWidth, width), out);
    }
  }

  public int width() { return width; }
}

/**
 * An indented series of chunks laid out vertically with list headers.
 */
final class ListBlock implements TextChunk {
  final List<TextChunk> items;
  /**
   * Given a zero-indexed item index, produces bullet text.
   * Must be repeatable, i.e. for the same integer >= 0 always returns the same
   * text.
   */
  final Function<Integer, String> bulletMaker;
  final int height, width, bulletWidth;
  ListBlock(List<TextChunk> items, Function<Integer, String> bulletMaker) {
    this.items = ImmutableList.copyOf(items);
    this.bulletMaker = bulletMaker;
    int bulletWidth = 0;  // min bullet height
    int itemWidth = 0;
    int height = 0;
    int idx = 0;
    for (TextChunk item : items) {
      String bullet = bulletMaker.apply(idx + 1);
      bulletWidth = Math.max(3 + bullet.length(), bulletWidth);
      itemWidth = Math.max(item.width(), itemWidth);
      height += item.height();
      ++idx;
    }
    this.height = height;
    this.width = itemWidth + bulletWidth;
    this.bulletWidth = bulletWidth;
  }
  public boolean breaks() { return true; }
  public int height() { return height; }
  public int width() { return width; }
  public void write(int containerWidth, StringBuilder out) {
    StringBuilder sb = new StringBuilder();
    int idx = 0;
    boolean sawOne = false;
    for (TextChunk item : items) {
      sb.setLength(0);
      item.write(containerWidth - bulletWidth, sb);
      String[] lines = PreformattedStaticHtml.splitLines(sb);
      String bullet = bulletMaker.apply(idx);
      ++idx;
      for (int i = 0; i < lines.length; ++i) {
        if (sawOne) { out.append('\n'); }
        sawOne = true;
        PreformattedStaticHtml.pad(out, bulletWidth - bullet.length() - 1);
        out.append(bullet);
        bullet = "";  // For second and subsequent lines.
        out.append(' ').append(lines[i]);
      }
    }
  }
}

/**
 * Represents an HTML table.
 */
final class Table implements TextChunk {
  final ImmutableList<Cell> cells;
  final int height;
  final int[] colWidths;
  final int width;

  private static final class Cell {
    final int x, y;
    final int colspan;
    final int rowspan;
    final TextChunk body;

    Cell(int x, int y, int colspan, int rowspan, TextChunk body) {
      this.x = x;
      this.y = y;
      this.colspan = colspan;
      this.rowspan = rowspan;
      this.body = body;
    }

    @Override public String toString() {
      return "[Cell @ (" + x + "+" + colspan + ", " + y + "+" + rowspan + ")]";
    }
  }

  private static void findRows(
      List<TextChunk> chunks, ImmutableList.Builder<TextChunk> rows) {
    for (TextChunk chunk : chunks) {
      if (chunk instanceof TextBlock) {
        findRows(((TextBlock) chunk).parts, rows);
      } else {
        rows.add(chunk);
      }
    }
  }

  Table(List<TextChunk> parts) {
    ImmutableList<TextChunk> rows;
    {
      ImmutableList.Builder<TextChunk> rowsBuilder = ImmutableList.builder();
      findRows(parts, rowsBuilder);
      rows = rowsBuilder.build();
    }
    int nCols = 0;
    int nRows = 0;
    {
      ImmutableList.Builder<Cell> cellsBuilder = ImmutableList.builder();
      List<BitSet> rowUsage = Lists.newArrayList();
      for (int y = 0, n = rows.size(); y < n; ++y) {
        TextChunk row = rows.get(y);
        List<TextChunk> cells;
        if (row instanceof InlineText) {
          cells = ((InlineText) row).parts;
        } else {
          cells = ImmutableList.of(row);
        }
        while (rowUsage.size() <= y) { rowUsage.add(new BitSet()); }
        BitSet freeCells = rowUsage.get(y);
        for (TextChunk cell : cells) {
          int colspan = 1, rowspan = 1;
          if (cell instanceof TableCell) {
            colspan = ((TableCell) cell).colspan;
            rowspan = ((TableCell) cell).rowspan;
          }
          int x = freeCells.nextClearBit(0);
          cellsBuilder.add(new Cell(x, y, colspan, rowspan, cell));
          int f = y + rowspan;
          while (rowUsage.size() < f) { rowUsage.add(new BitSet()); }
          for (int j = y; j < f; ++j) { rowUsage.get(j).set(x, x + colspan); }
        }
        nCols = Math.max(freeCells.cardinality(), nCols);
      }
      this.cells = cellsBuilder.build();
      nRows = rowUsage.size();
    }
    // Sort cells by rightmost column.
    // As long as the cells are seen left to right, we can compute the width
    // by making sure htat the rightmost column a cell occupies can contain its
    // overflow.
    int[] colWidths = new int[nCols];
    int width;
    int height;
    {
      Cell[] cellArr = new Cell[cells.size()];
      cells.toArray(cellArr);
      Arrays.sort(cellArr, new Comparator<Cell>() {
        public int compare(Cell a, Cell b) {
          return (a.x + a.colspan) - (b.x + b.colspan);
        }
      });
      for (Cell c : cellArr) {
        int endCol = c.x + c.colspan - 1;
        int bodyWidth = c.body.width()
            - (c.colspan - 1); // 1 space between each pair of adjacent columns
        for (int i = c.x; i < endCol; ++i) { bodyWidth -= colWidths[i]; }
        colWidths[endCol] = Math.max(colWidths[endCol], bodyWidth);
      }
      width = nCols - 1;  // 1 space between each pair of adjacent columns
      for (int colWidth : colWidths) { width += colWidth; }

      int[] rowHeights = new int[nRows];
      Arrays.sort(cellArr, new Comparator<Cell>() {
        public int compare(Cell a, Cell b) {
          return (a.y + a.rowspan) - (b.y + b.rowspan);
        }
      });
      for (Cell c : cellArr) {
        int endRow = c.y + c.rowspan - 1;
        int bodyHeight = c.body.height();
        for (int j = c.y; j < endRow; ++j) { bodyHeight -= rowHeights[j]; }
        rowHeights[endRow] = Math.max(rowHeights[endRow], bodyHeight);
      }
      height = 0;
      for (int rowHeight : rowHeights) { height += rowHeight; }
    }
    this.colWidths = colWidths;
    this.width = width;
    this.height = height;
  }

  public boolean breaks() { return true; }

  public int height() { return height; }

  public int width() { return width; }

  public void write(int containerWidth, StringBuilder out) {
    StringBuilder[] lines = new StringBuilder[height];
    for (int j = lines.length; --j >= 0;) {
      lines[j] = new StringBuilder(width);
    }
    int nCols = colWidths.length;
    int[] colStart = new int[colWidths.length + 1];
    for (int i = 0; i < nCols; ++i) {
      colStart[i + 1] = colStart[i] + colWidths[i] + 1;
    }
    PriorityQueue<CellContent> pq = new PriorityQueue<CellContent>();
    {
      StringBuilder cellContent = new StringBuilder();
      for (Cell c : cells) {
        cellContent.setLength(0);
        int cellWidth = colStart[c.x + c.colspan] - colStart[c.x] - 1;
        c.body.write(cellWidth, cellContent);
        String[] cellLines = PreformattedStaticHtml.splitLines(cellContent);
        if (cellLines.length != 0) { pq.add(new CellContent(c, cellLines)); }
      }
    }
    while (!pq.isEmpty()) {
      CellContent cc = pq.poll();
      Cell c = cc.c;
      int y = c.y + cc.line;
      int x0 = colStart[c.x];
      StringBuilder lineOut = lines[y];
      if (lineOut.length() < x0) {
        PreformattedStaticHtml.pad(lineOut, x0 - lineOut.length());
      }
      lineOut.append(cc.lines[cc.line]);
      if (++cc.line < cc.lines.length) { pq.add(cc); }
    }
    Joiner.on('\n').appendTo(out, lines);
  }

  static final class CellContent implements Comparable<CellContent> {
    final Cell c;
    /** the textual form of c.body. */
    final String[] lines;
    /** an index into lines. */
    int line;

    CellContent(Cell c, String[] lines) {
      this.c = c;
      this.lines = lines;
    }

    public int compareTo(CellContent other) {
      int delta = (c.y + line) - (other.c.y + other.line);
      if (delta != 0) { return delta; }
      return c.x - other.c.x;
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof CellContent)) { return false; }
      CellContent that = (CellContent) o;
      return this.c.equals(that.c) && this.line == that.line;
    }

    @Override public int hashCode() { return c.hashCode() + 31 * line; }
  }
}

/**
 * Wraps a table cell to prevent it from being coalesced by {@link InlineText}.
 */
final class TableCell implements TextChunk {
  final TextChunk body;
  final int colspan, rowspan;

  TableCell(TextChunk body, int colspan, int rowspan) {
    this.body = body;
    this.colspan = colspan;
    this.rowspan = rowspan;
  }

  public boolean breaks() { return false; }

  public int height() { return body.height(); }

  public int width() { return body.width(); }

  public void write(int containerWidth, StringBuilder out) {
    body.write(containerWidth, out);
  }
}

/** Centers its body in its container. */
final class CenteredText implements TextChunk {
  final TextChunk body;

  CenteredText(TextChunk body) { this.body = body; }

  public boolean breaks() { return true; }

  public int height() { return body.height(); }

  public int width() { return body.width(); }

  public void write(int containerWidth, StringBuilder out) {
    // Break out lines so we can center them individualy.
    String[] lines;
    {
      StringBuilder sb = new StringBuilder();
      body.write(containerWidth, sb);
      lines = PreformattedStaticHtml.splitLines(sb);
    }
    boolean sawOne = false;
    for (String line : lines) {
      if (sawOne) { out.append('\n'); }
      sawOne = true;
      int padding = (containerWidth - line.length()) / 2;
      if (padding > 0) { PreformattedStaticHtml.pad(out, padding); }
      out.append(line);
    }
  }
}
