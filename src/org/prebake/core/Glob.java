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

package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSON;
import org.prebake.js.YSONConverter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A pattern that matches a group of files.
 *
 * <p>Parts of the below talk about <b>normalized paths</b>.  Normalized paths
 * use {@code /} to separate individual file and directory names instead of
 * using the system dependent separator.  E.g., on Windows, the normalized form
 * of {@code C:\foo} is {@code C:/foo}.
 *
 * <h2>Tree Roots</h2>
 * A glob may specify that a prefix of all paths matched by it is the root
 * of a directory tree, such as <ul>
 *   <li>Portion of a C header file include directory,</li>
 *   <li>A java package tree,</li>
 *   <li>The root of files in a JAR or ZIP archive,</li>
 *   <li>The directory to contain the {@code index.html} file in a tree of
 *       HTML reports,</li>
 *   <li>etc.</li>
 * </ul>
 * The tree root of a glob is the prefix of it that precedes 3 path separators.
 * E.g. the tree root of {@code src///org/prebake/**.java} is {@code src}, and
 * the rest of the prefix, {@code /org/prebake} are path elements under the tree
 * root. That glob matches paths like {@code src/org/prebake/Foo.java} ; the
 * paths it matches do not have to have tripled file separators.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/Glob">Wiki Docs</a>
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Glob implements Comparable<Glob>, JsonSerializable {
  private final int treeRootIndex;
  private final String[] parts;
  private final @Nullable String[] holes;
  private transient Pattern regex;

  private Glob(int treeRootIndex, String[] parts, @Nullable String[] holes) {
    this.treeRootIndex = treeRootIndex;
    this.parts = parts;
    this.holes = holes;
  }

  private static final String[] ZERO_STRINGS = new String[0];

  /** See the grammar at http://code.google.com/p/prebake/wiki/Glob. */
  public static Glob fromString(String s) {
    int n = s.length();
    int partCount = 0;
    int treeRootIndex = 0;
    int nHoles = 0;
    boolean hasNamedHoles = false;
    for (int i = 0; i < n; ++i) {
      ++partCount;
      switch (s.charAt(i)) {
        case '*':  // * or **
          if (i + 1 < n && s.charAt(i + 1) == '*') {
            ++i;
            // Three adjacent *'s not allowed.
            if (i + 1 < n && s.charAt(i + 1) == '*') { badGlob(s); }
          }
          if (i + 1 < n && s.charAt(i + 1) == '(') {
            i = s.indexOf(')', i + 2);
            if (i < 0) { badGlob(s); }
            hasNamedHoles = true;
          }
          ++nHoles;
          break;
        case '/': {
          // Two adjacent path separators not allowed unless as part of a tree
          // root index.
          int start = i;
          while (i + 1 < n && s.charAt(i + 1) == '/') { ++i; }
          switch (i - start) {  // number of extra slashes
            case 0: break;
            case 2:
              if (treeRootIndex != 0 || nHoles != 0) { badGlob(s); }
              treeRootIndex = partCount - 1;
              break;
            default: badGlob(s);
          }
          break;
        }
        default:
          while (i + 1 < n) {
            char next = s.charAt(i + 1);
            if (next == '/') { break; }
            // * must follow / or start the glob.
            if (next == '*') { badGlob(s); }
            ++i;
          }
          break;
      }
    }
    String[] parts = new String[partCount];
    String[] holes = nHoles == 0 ? ZERO_STRINGS : new String[nHoles];
    int p = -1, h = 0;
    for (int i = 0; i < n; ++i) {
      int start = i;
      switch (s.charAt(i)) {
        case '*':
          if (i + 1 < n && s.charAt(i + 1) == '*') { ++i; }
          parts[++p] = i == start ? "*" : "**";
          if (i + 1 < n && s.charAt(i + 1) == '(') {
            int end = s.indexOf(')', i + 2);
            String hole = holes[h] = s.substring(i + 2, end);
            if (!YSON.isValidIdentifierName(hole)) { badGlob(s); }
            i = end;
          }
          ++h;
          break;
        case '/':
          if (p != -1 && p + 1 == treeRootIndex) {
            i = start += 2;
          }
          parts[++p] = i == start ? "/" : "///";
          break;
        default:
          while (i + 1 < n) {
            char next = s.charAt(i + 1);
            if (next == '/') { break; }
            ++i;
          }
          String part = s.substring(start, i + 1);
          // . and .. are not allowed as path parts.
          switch (part.length()) {
            case 0: throw new IllegalStateException();
            case 2: if ('.' != part.charAt(1)) { break; }
            // $FALL-THROUGH$
            case 1: if ('.' == part.charAt(0)) { badGlob(s); } break;
            default: break;  // OK
          }
          parts[++p] = part;
          break;
      }
    }
    return new Glob(treeRootIndex, parts, hasNamedHoles ? holes : null);
  }

  private static void badGlob(String glob) {
    throw new GlobSyntaxException(glob);
  }

  private static final class Strategy {
    final byte iIncr;
    final byte jIncr;
    final byte outPart;
    final byte toCheck;
    Strategy(int iIncr, int jIncr, int outPart, int toCheck) {
      this.iIncr = (byte) iIncr;
      this.jIncr = (byte) jIncr;
      this.outPart = (byte) outPart;
      this.toCheck = (byte) toCheck;
    }
  }
  /**
   * A lookup table for strategies used to try to compute intersection.
   * The indexes are a bitwise-or of two values.
   * For the left part<pre>
   * 0 if it is textual,
   * 1 if it is '*',
   * 2 if it is '**'
   * </pre> and for the right part<pre>
   * 0 if it is textual,
   * 4 if it is '*',
   * 8 if it is '**'
   * </pre>.
   * Each element is a number of strategies.
   * Each strategy has four parts:
   * <ol>
   *   <li>The amount to increment the first part index
   *   <li>The amount to increment the second part index
   *   <li>The part to use in the output or -1 if none.
   *   <li>The part to check or -1 if none.  If the checked part is '/' the
   *   strategy is not applicable.
   * </ol>
   */
  private static final Strategy[][] STRATEGIES = new Strategy[][] {
        null,                             // 0  aa, bb
        { new Strategy(0, 1, 1, 1),       // 1  *,  aa
          new Strategy(1, 0, -1, -1) },
        { new Strategy(0, 1, 1, -1),      // 2  **, aa
          new Strategy(1, 0, -1, -1) },
        null,                             // 3
        { new Strategy(1, 0, 0, 0),       // 4  aa, *
          new Strategy(0, 1, -1, -1) },
        { new Strategy(0, 1, 0, -1),      // 5  *,  *
          new Strategy(1, 0, 0, -1),
          new Strategy(1, 1, 0, -1) },
        { new Strategy(0, 1, 1, -1),      // 6  **, *
          new Strategy(1, 1, 1, -1) },
        null,                             // 7
        { new Strategy(1, 0, 0, -1),      // 8  aa, **
          new Strategy(0, 1, -1, -1) },
        { new Strategy(1, 0, 0, -1),      // 9  *,  **
          new Strategy(1, 1, 0, -1) },
        { new Strategy(0, 1, 0, -1),      // 10 **, **
          new Strategy(1, 0, 0, -1),
          new Strategy(1, 1, 0, -1) }
  };
  // For the above, the following must hold.
  // (1) The first two elements of any strategy must not be either 0 or 1,
  //     and cannot both be zero.  Else intersection would not be halting.
  // (2) The third element must be in [0, 1, -1].
  // (3) The fourth element must be in [0, 1, -1] and should be the
  //     index of the textual element if one is '*'.
  //     I.e., if one pair of bits is 0 and the other pair of bits is 1, then
  //     the fourth element is the index of the zeroed pair.
  //     E.g. on for (1 => 1, 4 => 0, else => -1).

  /**
   * A glob that matches only (but not necessarily all) paths matched by both
   * p and q, or null if no such path exists.
   */
  public static Glob intersection(Glob p, Glob q) {
    return new Intersector(p, q).intersection();
  }

  /**
   * True iff there exists a path that is matched by at least one of the globs
   * in unionA and at least one of the globs in unionB.
   */
  public static boolean overlaps(Iterable<Glob> unionA, Iterable<Glob> unionB) {
    for (Glob a : unionA) {
      for (Glob b : unionB) {
        if (new Intersector(a, b).intersects()) { return true; }
      }
    }
    return false;
  }

  /**
   * A function that transforms paths that match the input glob into paths
   * that match the output glob.
   * @return a function that takes a normalized path
   *     (<tt>/</tt> as the separator) relative to the client root and returns
   *     a normalized path relative to the client root.
   *     The function will return null if the path does not match the input
   *     glob.
   */
  public static Function<String, String> transform(Glob input, Glob output)
      throws IllegalArgumentException {
    int m = input.parts.length, n = output.parts.length;
    // The literal portions of the output glob without connecting slashes.
    // For the output glob "foo/**/bar/baz/*.html", the literal portions are
    // ["foo", "bar/baz", ".html"]
    List<String> literals = Lists.newArrayList();
    // For each hole, true if the hole is preceded by a slash in the output.
    final boolean[] precededBySlash;
    final boolean[] followedBySlash;
    // Number of holes in input that don't correspond to any in the output.
    // E.g., for (input="**/foo/*.txt", output="bar/*.txt")
    // which would map { a/foo/x.txt => bar/x.txt, b/c/foo/y.txt => bar/y.txt }
    // nUnusedGroups is 1 since the "**" isn't used in the output.
    final int nUnusedGroups;
    {
      List<Boolean> precededBySlashList = Lists.newArrayList();
      List<Boolean> followedBySlashList = Lists.newArrayList();
      List<String> outputPartsList = Arrays.asList(output.parts);
      Joiner joiner = Joiner.on("");
      int pos = n;
      int j = m;
      // Iterate in reverse so that unused groups fall at the beginning of the
      // input.  We do this because the tail ends of a path are
      // more-significant.  I.e., for input "*/*.foo", and output "*.bar",
      // we want to map "x/y.foo" to "y.bar", not "x.bar".
      for (int i = n; --i >= 0;) {
        String outPart = output.parts[i];
        if (outPart.charAt(0) != '*') { continue; }
        String inPart = null;
        while (--j >= 0) {
          String part = input.parts[j];
          if (part.charAt(0) == '*') {
            inPart = part;
            break;
          }
        }
        // We need to verify that for every wildcard in output, there is a
        // corresponding wildcard in input that matches a subset.
        if (inPart == null) {
          throw new IllegalArgumentException(
              "Can't transform " + input + " to " + output + "."
              + "  There is no corresponding hole for "
              + Joiner.on("").join(
                  Arrays.asList(output.parts).subList(0, i + 1)));
        } else if ("**".equals(inPart) && "*".equals(outPart)) {
          throw new IllegalArgumentException(
              "Can't transform " + input + " to " + output + "."
              + "  There is no corresponding hole for the " + outPart
              + " at the end of " + Joiner.on("").join(
                  Arrays.asList(output.parts).subList(0, i + 1)));
        }
        int k = i + 1;
        if (k < pos && "/".equals(outputPartsList.get(k))) { ++k; }
        literals.add(joiner.join(outputPartsList.subList(k, pos)));
        pos = i;
        boolean slashBefore = i > 0 && "/".equals(outputPartsList.get(i - 1));
        if (slashBefore) { --pos; }
        precededBySlashList.add(slashBefore);
        boolean slashAfter = i + 1 < n
            && "/".equals(outputPartsList.get(i + 1));
        followedBySlashList.add(slashAfter);
      }
      literals.add(joiner.join(outputPartsList.subList(0, pos)));
      // Since we iterated above in reverse order.
      Collections.reverse(literals);
      {
        int np = precededBySlashList.size();
        // 1-indexed to simplify indexing in function loop below.
        precededBySlash = new boolean[np + 1];
        followedBySlash = new boolean[np + 1];
        for (int a = np, b = 0; --a >= 0;) {
          precededBySlash[++b] = precededBySlashList.get(a);
          followedBySlash[b] = followedBySlashList.get(a);
        }
      }
      int unusedGroups = 0;
      while (--j >= 0) {
        if (input.parts[j].charAt(0) == '*') { ++unusedGroups; }
      }
      nUnusedGroups = unusedGroups;
    }
    final Pattern inPattern;
    {
      StringBuilder inBuf = new StringBuilder();
      input.toRegex("/", true, inBuf);
      inPattern = Pattern.compile(inBuf.toString(), Pattern.DOTALL);
    }
    final String[] outputParts = literals.toArray(new String[literals.size()]);
    final int nSubs = outputParts.length - 1;
    final int lenDelta;  // Used to presize output StringBuilder.
    {
      int delta = 16;
      for (int i = n; --i >= 0;) { delta += output.parts[i].length(); }
      for (int i = m; --i >= 0;) { delta -= input.parts[i].length(); }
      lenDelta = delta;
    }
    return new Function<String, String>() {
      public String apply(String inputPath) {
        Matcher m = inPattern.matcher(inputPath);
        if (!m.matches()) { return null; }
        StringBuilder sb = new StringBuilder(
            Math.max(0, lenDelta + inputPath.length()));
        sb.append(outputParts[0]);
        boolean needSlash = false;
        for (int i = 1; i <= nSubs; ++i) {
          if (precededBySlash[i]) { needSlash = true; }
          String group = m.group(i + nUnusedGroups);
          if (group != null && group.length() != 0) {
            if (needSlash) {
              sb.append('/');
              needSlash = false;
            }
            sb.append(group);
            if (followedBySlash[i]) { needSlash = true; }
          }
          String outputPart = outputParts[i];
          if (outputPart.length() != 0) {
            if (needSlash) {
              sb.append('/');
              needSlash = false;
            }
            sb.append(outputPart);
          }
        }
        return sb.toString();
      }
    };
  }

  /**
   * A prefix common to all these globs that is also a path that is an ancestor
   * (non-strict) of all paths matched by any of the input globs.
   * @return {@code ""} if there are no inputs.
   */
  public static String commonPrefix(Iterable<Glob> globs) {
    Iterator<Glob> it = globs.iterator();
    if (!it.hasNext()) { return ""; }
    Glob first = it.next();
    int commonPrefixLen = 0;
    while (commonPrefixLen < first.parts.length
           && first.parts[commonPrefixLen].charAt(0) != '*') {
      ++commonPrefixLen;
    }
    while (it.hasNext() && commonPrefixLen > 0) {
      Glob glob = it.next();
      if (glob.parts.length < commonPrefixLen) {
        commonPrefixLen = glob.parts.length;
      }
      for (int i = 0; i < commonPrefixLen; ++i) {
        if (!glob.parts[i].equals(first.parts[i])) {
          commonPrefixLen = i;
          break;
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < commonPrefixLen; ++i) { sb.append(first.parts[i]); }
    return sb.toString();
  }

  /**
   * A value that if passed to {@link #fromString} would return an
   * {@link #equals equivalent} glob.
   */
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    if (treeRootIndex != 0) {
      for (int i = 0; i < treeRootIndex; ++i) { sb.append(parts[i]); }
      sb.append("//");
    }
    for (int i = treeRootIndex, h = 0, n = parts.length; i < n; ++i) {
      String part = parts[i];
      sb.append(part);
      if (holes != null && part.charAt(0) == '*') {
        String name = holes[h];
        if (name != null) { sb.append('(').append(name).append(')'); }
      }
    }
    return sb.toString();
  }

  public String getTreeRoot() {
    if (treeRootIndex == 0) { return ""; }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < treeRootIndex; ++i) { sb.append(parts[i]); }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Glob)) { return false; }
    Glob that = (Glob) o;
    if (this.treeRootIndex != that.treeRootIndex) { return false; }
    return Arrays.equals(this.parts, that.parts);
  }

  @Override
  public int hashCode() { return Arrays.hashCode(parts); }

  List<String> parts() {
    return Collections.unmodifiableList(Arrays.asList(parts));
  }

  void enumerateHoleNamesOnto(Collection<? super String> out) {
    if (holes == null) { return; }
    for (String hole : holes) { if (hole != null) { out.add(hole); } }
  }

  /**
   * The most specific path which is a strict ancestor of all paths matched by
   * this glob.
   * @param base the base directory.
   * @return a path resolved against base.
   */
  public Path getPathContainingAllMatches(Path base) {
    int end = 0;
    int n = parts.length;
    for (; end < n; ++end) {
      String part = parts[end];
      if (part.charAt(0) == '*') { break; }
    }
    if (end == 0) { return base; }
    int start = 0;
    if ("/".equals(parts[0])) {
      base = base.getRoot();
      start = 1;
    }
    if (end > start && "/".equals(parts[end - 1])) {
      --end;
    } else if (end == n) {
      // A glob with no wildcards could match an exact file or an exact
      // directory, so back up to make sure we match only dirs.
      --end;
    }
    if (end == start) { return base; }
    StringBuilder sb = new StringBuilder();
    String sep = base.getFileSystem().getSeparator();
    for (int i = start; i < end; ++i) {
      String part = parts[i];
      sb.append("/".equals(part) ? sep : part);
    }
    return base.resolve(sb.toString());
  }

  public int compareTo(Glob that) {
    int m = this.parts.length;
    int n = that.parts.length;
    for (int i = 0, limit = Math.min(m, n); i < limit; ++i) {
      int delta = this.parts[i].compareTo(that.parts[i]);
      if (delta != 0) { return delta; }
    }
    return m - n;
  }

  private static class Intersector {
    final String[] p, q;
    final int m, n;
    final int treeRootIndex;

    Intersector(Glob p, Glob q) {
      this.p = p.parts;
      this.q = q.parts;
      this.m = this.p.length;
      this.n = this.q.length;
      this.treeRootIndex = Math.min(p.treeRootIndex, q.treeRootIndex);
    }

    Glob intersection() {
      {
        // The recursive algorithm below does a good job of early outing in the
        // common no-intersection case where the two globs have a different
        // prefix.
        // But there is a very common case with differing suffixes which has
        // worst case behavior : foo/bar/baz/*.x and foo/bar/baz/*.y
        // This early-outs on the different suffix case.
        int lastp = p.length - 1, lastq = q.length - 1;
        if (lastp >= 0 && lastq >= 0) {
          String pend = p[lastp], qend = q[lastq];
          if (pend.charAt(0) != '*' && qend.charAt(0) != '*') {
            if (pend.length() > qend.length()) {
              if (!pend.endsWith(qend)) { return null; }
            } else {
              if (!qend.endsWith(pend)) { return null; }
            }
          }
        }
      }

      List<String> partsReversed = inter(0, false, 0, false);
      if (partsReversed == null) { return null; }
      int np = partsReversed.size();
      String[] parts = new String[partsReversed.size()];
      for (int i = np; --i >= 0;) {
        parts[i] = partsReversed.get(np - i - 1);
      }
      return new Glob(treeRootIndex, parts, null);
    }

    boolean intersects() {
      return inter(0, false, 0, false) != null;
    }

    private List<String> inter(int i, boolean isuffix, int j, boolean jsuffix) {
      String a, b;
      if (i < m) {
        a = p[i];
      } else {
        a = "";
      }
      int alen = a.length();
      if (j < n) {
        b = q[j];
      } else if (alen == 0) {
        return Lists.newArrayListWithCapacity(i + j);  // Success
      } else {
        b = "";
      }
      int blen = b.length();
      int strategy_idx = 0;
      if (alen != 0 && a.charAt(0) == '*') {
        if (blen == 0) { return inter(i + 1, true, j, jsuffix); }
        strategy_idx |= alen;
      }
      if (blen != 0 && b.charAt(0) == '*') {
        if (alen == 0) { return inter(i, isuffix, j + 1, true); }
        strategy_idx |= blen << 2;
      }
      if (strategy_idx == 0) {
        String match;
        if (isuffix) {
          if (jsuffix) {
            if (a.length() >= b.length()) {
              if (!a.endsWith(b)) { return null; }
              match = a;
            } else if (!b.endsWith(a)) {
              return null;
            } else {
              match = b;
            }
          } else if (!b.endsWith(a)) {
            return null;
          } else {
            match = b;
          }
        } else if (jsuffix) {
          if (!a.endsWith(b)) { return null; }
          match = a;
        } else if (!a.equals(b)) {
          return null;
        } else {
          match = a;
        }
        List<String> result = inter(i + 1, false, j + 1, false);
        if (result != null) { result.add(match); }
        return result;
      }
      isuffix = a.charAt(0) == '*';
      jsuffix = b.charAt(0) == '*';
      for (Strategy strategy : STRATEGIES[strategy_idx]) {
        switch (strategy.toCheck) {
          case 0: if (alen == 1 && '/' == a.charAt(0)) { continue; } break;
          case 1: if (blen == 1 && '/' == b.charAt(0)) { continue; } break;
        }
        List<String> result = inter(
            i + strategy.iIncr, isuffix, j + strategy.jIncr, jsuffix);
        if (result != null) {
          switch (strategy.outPart) {
            case 0: result.add(a); break;
            case 1: result.add(b); break;
            default: break;  // output nothing
          }
          return result;
        }
      }
      return null;
    }
  }

  /** The JSON form of a glob is a string literal. */
  public void toJson(JsonSink sink) throws IOException {
    sink.writeValue(toString());
  }

  /** True iff this glob matches the given path. */
  public boolean match(String path) { return match(path, null); }

  /**
   * True iff this glob matches the given path given the given bindings.
   * @param parameterBindings if non-null, then any named parameters must match
   *    keys that are present in the given bindings map.  If the match is
   *    successful any parameters whose names were not in the map will be put
   *    into this map.
   */
  public boolean match(
      String path, @Nullable Map<String, String> parameterBindings) {
    if (regex == null) {
      StringBuilder sb = new StringBuilder();
      toRegex("/\\\\", holes != null, sb);
      regex = Pattern.compile(sb.toString(), Pattern.DOTALL);
    }
    Matcher m = regex.matcher(path);
    if (!m.matches()) { return false; }
    if (holes != null) {
      Map<String, String> existingBindings = parameterBindings != null
          ? parameterBindings : Collections.<String, String>emptyMap();
      Map<String, String> newBindings = Maps.newLinkedHashMap();

      for (int i = 0, n = holes.length; i < n; ++i) {
        @Nullable String parameterName = holes[i];
        if (parameterName == null) { continue; }
        String bindingValue = existingBindings.get(parameterName);
        String value = m.group(i + 1);
        value = value != null ? value.replace('\\', '/') : "";
        if (bindingValue == null) {
          String existingBinding = newBindings.put(parameterName, value);
          if (existingBinding != null && !existingBinding.equals(value)) {
            // Contradictory binding
            return false;
          }
        } else if (!bindingValue.equals(value)) {
          return false;
        }
      }
      if (parameterBindings != null) {
        parameterBindings.putAll(newBindings);
      }
    }
    return true;
  }

  /** Substitute parameter bindings to get a more specific glob. */
  public Glob subst(Map<String, String> bindings) {
    if (holes == null) { return this; }
    StringBuilder sb = new StringBuilder();
    int h = 0;
    boolean pendingSep = false;  // True if we need to write out a separator
    for (int i = 0, n = parts.length; i < n; ++i) {
      String part = parts[i];
      switch (part.charAt(0)) {
        case '/':
          // Put a separator at the beginning of /foo. but not at the beginning
          // of *(a)/foo when a is blank.
          pendingSep = i == 0 || sb.length() != 0;
          break;
        case '*':
          String holeName = holes[h++];
          if (holeName != null) {
            String value = bindings.get(holeName);
            if (value != null && !"".equals(value)) {
              if (pendingSep) {
                if (!value.startsWith("/")) { sb.append('/'); }
                pendingSep = false;
              }
              if (value.endsWith("/")) {
                pendingSep = true;
                value = value.substring(0, value.length() - 1);
              }
              sb.append(value);
            }
          } else {
            if (pendingSep) {
              sb.append('/');
              pendingSep = false;
            }
            sb.append(part);
          }
          break;
        default:
          if (pendingSep) {
            sb.append('/');
            pendingSep = false;
          }
          sb.append(part);
          break;
      }
    }
    // Special case /.  Normally we ignore a sep at the end to avoid putting
    // the trailing / in foo/ for consistency.
    if (pendingSep && sb.length() == 0) { return Glob.fromString("/"); }
    return Glob.fromString(sb.toString());
  }

  private void toRegex(
      String separatorChars, boolean capture, StringBuilder sb) {
    for (int i = 0, n = parts.length; i < n; ++i) {
      String part = parts[i];
      switch (part.charAt(0)) {
        case '*':
          if (part.length() == 2) {
            if (i + 1 < n && "/".equals(parts[i + 1])) {
              // foo/**/bar should match foo/bar.
              sb.append("(?:").append(capture ? "(.+)" : ".+")
                  .append("[").append(separatorChars).append("])?");
              ++i;
            } else {
              sb.append(capture ? "(.*)" : ".*");
            }
          } else {
            if (capture) { sb.append('('); }
            sb.append("[^").append(separatorChars).append("]*");
            if (capture) { sb.append(')'); }
          }
          break;
        case '/':
          if (i + 2 == n) {
            String nextPart = parts[i + 1];
            if ('*' == nextPart.charAt(0)) {
              // foo/* and foo/** should match foo
              sb.append("(?:");
              if (nextPart.length() == 2) {
                sb.append('[').append(separatorChars)
                    .append(capture ? "](.*)" : "].*");
              } else {
                sb.append('[').append(separatorChars)
                    .append(capture ? "]([^" : "][^")
                    .append(separatorChars)
                    .append(capture ? "]*)" : "]*");
              }
              sb.append(")?");
              ++i;
            } else {
              sb.append("[").append(separatorChars).append("]");
            }
          } else {
            sb.append("[").append(separatorChars).append("]");
            // foo/ should match foo
            if (i + 1 == n) { sb.append('?'); }
          }
          break;
        default:
          sb.append(Pattern.quote(part));
          break;
      }
    }
  }

  public static Pattern toRegex(Iterable<Glob> globs) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Glob g : globs) {
      if (!first) { sb.append('|'); }
      first = false;
      g.toRegex("/\\\\", false, sb);
    }
    return Pattern.compile(sb.toString(), Pattern.DOTALL);
  }

  /**
   * Does shell-style expansion of <tt>{foo,bar}</tt> sections to produce
   * a list of globs from a single input glob.
   * E.g. <tt>foo/**.{html,js,css}</tt> would expand to the globs
   * <tt>[foo/**.html, foo/**.js, foo/**.css]</tt>.
   */
  public static final YSONConverter<ImmutableList<Glob>> CONV
      = new YSONConverter<ImmutableList<Glob>>() {
    public @Nullable ImmutableList<Glob> convert(
        @Nullable Object ysonValue, MessageQueue problems) {
      ImmutableList.Builder<Glob> globs = ImmutableList.builder();
      if (ysonValue instanceof String) {
        expandOneGlob((String) ysonValue, globs, problems);
      } else if (ysonValue instanceof List<?>) {
        for (Object o : ((List<?>) ysonValue)) {
          if (o instanceof String) {
            expandOneGlob((String) o, globs, problems);
          } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Expected ").append(exampleText()).append(" not ");
            try {
              new JsonSink(sb).writeValue(o).close();
            } catch (IOException ex) {
              Throwables.propagate(ex);
            }
            problems.error(sb.toString());
          }
        }
      }
      return globs.build();
    }
    public String exampleText() { return "['*.glob', ...]"; }
  };

  private static final Pattern SHELL_EXP
      = Pattern.compile("\\{([^,\\}]*,[^\\}]*)\\}");
  private static void expandOneGlob(
      String glob, final ImmutableList.Builder<? super Glob> out,
      MessageQueue mq) {
    Matcher m = SHELL_EXP.matcher(glob);
    if (!m.find()) {
      try {
        out.add(Glob.fromString(normGlob(glob)));
      } catch (GlobSyntaxException ex) {
        mq.error("Bad glob: '" + glob + "'");
      }
      return;
    }
    final List<String> lits = Lists.newArrayList();
    final List<List<String>> options = Lists.newArrayList();
    int pos = 0;
    do {
      lits.add(glob.substring(pos, m.start()));
      List<String> opts = Lists.newArrayList();
      for (String opt : Splitter.on(',').split(m.group(1))) { opts.add(opt); }
      options.add(opts);
      pos = m.end();
    } while (m.find());
    lits.add(glob.substring(pos));
    try {
      class Expander {
        StringBuilder sb = new StringBuilder();
        void expand(int i) {
          sb.append(lits.get(i));
          if (i == options.size()) {
            out.add(Glob.fromString(normGlob(sb.toString())));
          } else {
            int len = sb.length();
            for (String opt : options.get(i)) {
              sb.replace(len, sb.length(), opt);
              expand(i + 1);
            }
          }
        }
      }
      new Expander().expand(0);
    } catch (GlobSyntaxException ex) {
      mq.error(
          "Bad glob '" + ex.getMessage() + "' expanded from '" + glob + "'");
    }
  }

  static String normGlob(String glob) {
    {  // Fold adjacent file separators.
      StringBuilder sb = null;
      int n = glob.length();
      int pos = 0;
      for (int i = 0; i < n; ++i) {
        if (glob.charAt(i) != '/') { continue; }
        int end = i;
        while (++end < n && glob.charAt(end) == '/') { /* ok */ }
        int nSlashes = end - i;
        if (nSlashes == 2) {
          // Two can be the result of lazy concatenation,
          // but three is a root marker.
          if (sb == null) { sb = new StringBuilder(n - 1); }
          sb.append(glob, pos, i + 1);
          pos = end;
        }
        i = end;
      }
      if (sb != null) { glob = sb.append(glob, pos, n).toString(); }
    }
    int n = glob.length();
    // Remove / at the end of a path.
    if (n > 1 && glob.charAt(n - 1) == '/') { glob = glob.substring(0, n - 1); }
    return glob;
  }

  /** Thrown when trying to convert a malformed string into a glob. */
  public static class GlobSyntaxException extends IllegalArgumentException {
    public GlobSyntaxException(String glob) { super(glob); }
  }
}
