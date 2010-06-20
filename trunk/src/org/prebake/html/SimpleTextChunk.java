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

/** A chunk of text, that might span multiple lines. */
class SimpleTextChunk implements TextChunk {
  final String s;
  final int height;
  final int width;

  SimpleTextChunk(final String s) {
    int height = 1;  // Number of lines before index i in s.
    int width = 0;  // Width of the longest line before index i in s.
    int start = 0;  // The index on which the current line started.
    int pos = 0;
    int n = s.length();
    StringBuilder sb = null;
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\n':
          width = Math.max(width, i - start);
          ++height;
          start = i + 1;
          break;
        case '\r':
        case '\u0085': case '\u2028': case '\u2029':
          // Normalize newlines so we only have to deal with them in one place.
          if (sb == null) { sb = new StringBuilder(n); }
          sb.append(s, pos, i).append('\n');
          width = Math.max(width, i - start);
          ++height;
          // Handle the two code-unit sequence CRLF.
          if (ch == '\r' && i + 1 < n && s.charAt(i + 1) == '\n') { ++i; }
          pos = start = i + 1;
          break;
      }
    }
    width = Math.max(n - start, width);
    this.s = sb == null ? s : sb.append(s, pos, n).toString();
    this.height = height;
    this.width = width;
  }

  public boolean breaks() { return false; }
  public int height() { return height; }
  public void write(int containerWidth, StringBuilder sb) { sb.append(s); }
  public int width() { return width; }
}
