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

import com.google.common.collect.ImmutableList;

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
      String[] partLines = TextUtil.splitLines(sb);
      int partWidth = part.width();
      for (int j = 0; j < height; ++j) {
        int toFill = partWidth;
        if (j < partLines.length) {
          lines[j].append(partLines[j]);
          toFill -= partLines[j].length();
        }
        TextUtil.pad(lines[j], toFill);
      }
    }
    for (int i = 0; i < height; ++i) {
      if (i != 0) { out.append('\n'); }
      out.append(lines[i]);
    }
  }

  public int width() { return width; }
}
