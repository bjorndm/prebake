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

final class TextUtil {
  private TextUtil() { /* uninstantiable */ }

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

  private static String[] NO_STRINGS = new String[0];
  /**
   * Splits around POSIX newlines returning the empty array for the empty
   * string.  We assume that no Windows or old Mac style newlines reach this
   * code since SimpleTextChunk normalizes newlines.
   */
  static String[] splitLines(CharSequence cs) {
    int n = cs.length();
    if (n == 0) { return NO_STRINGS; }
    String s = cs.toString();
    int nLines = 1;
    for (int i = -1; (i = s.indexOf('\n', i + 1)) >= 0;) { ++nLines; }
    String[] lines = new String[nLines];
    int start = 0;
    int k = -1;
    for (int end; (end = s.indexOf('\n', start)) >= 0; start = end + 1) {
      lines[++k] = s.substring(start, end);
    }
    lines[++k] = s.substring(start, n);
    return lines;
  }

  /** Renders an integer as an upper-case Roman numeral. */
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
    for (int i = sb.length() - 3; --i >= 0;) {
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
