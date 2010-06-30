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
 * Represents a chunk of HTML along with sizing and positioning info.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface TextChunk {
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
