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
import com.google.common.collect.Lists;

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
