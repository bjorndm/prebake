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
