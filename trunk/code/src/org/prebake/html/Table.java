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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
        String[] cellLines = TextUtil.splitLines(cellContent);
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
        TextUtil.pad(lineOut, x0 - lineOut.length());
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
