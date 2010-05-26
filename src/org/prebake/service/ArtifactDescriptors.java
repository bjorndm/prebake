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

package org.prebake.service;

/**
 * Maps {@link org.prebake.fs.NonFileArtifact}s to strings that can be part of
 * a file path for use with a {@link LogHydra}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class ArtifactDescriptors {
  // Below we stick a suffix on the end of the file name
  // so that windows will never treat it as executable as it might a .bat or
  // .exe file.

  public static String forTool(String toolName) {
    // Tool names are already valid JS identifiers but we want to dodge
    // character encoding issues.
    return toUnambiguousFileName(toolName, ".tool");
  }

  public static String forProduct(String productName) {
    // Product names are free-form text, so we need to encode file path and
    // shell specials to avoid confusing command line tools.
    return toUnambiguousFileName(productName, ".product");
  }

  public static String forPlanFile(String planFile) {
    return toUnambiguousFileName(planFile, ".plan");
  }

  static String toUnambiguousFileName(String s, String suffix) {
    StringBuilder sb = new StringBuilder(s.length() + 16);
    // Allow anything in [a-z0-9-_] but encode uppercase letters as
    // .<lowercase> and others as
    //   +hex+
    // where hex is the hex characters of one or more UTF-16 code-units
    // separated by dots.
    int n = s.length();
    boolean inFileSafeRun = true;
    int pos = 0;
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      if (isFileSafeChar(ch)) {
        if (inFileSafeRun) {
          if ('A' <= ch && ch <= 'Z') {
            sb.append(s, pos, i).append('.').append((char) (ch + ('a' - 'A')));
            pos = i + 1;
          }
        } else {
          sb.append('+');  // End unsafe run.
          pos = i;
          inFileSafeRun = true;
        }
      } else {
        if (inFileSafeRun) {
          sb.append(s, pos, i).append('+');
          inFileSafeRun = false;
        } else {
          sb.append('.');
        }
        hex(ch, sb);
      }
    }
    if (inFileSafeRun) {
      sb.append(s, pos, n);
    } else {
      sb.append('+');
    }
    return sb.append(suffix).toString();
  }

  private static boolean isFileSafeChar(char ch) {
    if ('-' > ch || ch > 'z') { return false; }
    if (ch <= 'Z') {
      return ch >= 'A' || (ch >= '0' ? ch <= '9' : ch == '-');
    } else {
      return ch == '_' || ch >= 'a';
    }
  }

  private static final char[] HEX = new char[] {
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
  };
  private static void hex(int ch, StringBuilder sb) {
    // Will output nothing for NUL which is fine.
    int start = sb.length();
    while (ch != 0) {
      sb.append(HEX[ch & 0xf]);
      ch = ch >>> 4;
    }
    int end = sb.length();
    // Reverse to get most sig digits on left
    while (--end > start) {
      char t = sb.charAt(start);
      sb.setCharAt(start, sb.charAt(end));
      sb.setCharAt(end, t);
      ++start;
    }
  }
}
