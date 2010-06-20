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

/** Centers its body in its container. */
final class CenteredText implements TextChunk {
  final TextChunk body;

  CenteredText(TextChunk body) { this.body = body; }

  public boolean breaks() { return true; }

  public int height() { return body.height(); }

  public int width() { return body.width(); }

  public void write(int containerWidth, StringBuilder out) {
    // Break out lines so we can center them individually.
    String[] lines;
    {
      StringBuilder sb = new StringBuilder();
      body.write(containerWidth, sb);
      lines = TextUtil.splitLines(sb);
    }
    boolean sawOne = false;
    for (String line : lines) {
      if (sawOne) { out.append('\n'); }
      sawOne = true;
      int padding = (containerWidth - line.length()) / 2;
      if (padding > 0) { TextUtil.pad(out, padding); }
      out.append(line);
    }
  }
}
