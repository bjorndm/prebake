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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

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
      String[] lines = TextUtil.splitLines(sb);
      String bullet = bulletMaker.apply(idx);
      ++idx;
      for (int i = 0; i < lines.length; ++i) {
        if (sawOne) { out.append('\n'); }
        sawOne = true;
        TextUtil.pad(out, bulletWidth - bullet.length() - 1);
        out.append(bullet);
        bullet = "";  // For second and subsequent lines.
        out.append(' ').append(lines[i]);
      }
    }
  }
}
