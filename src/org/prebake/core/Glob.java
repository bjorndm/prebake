package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A pattern that matches a group of files.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/Glob">Wiki Docs</a>
 * @author mikesamuel@gmail.com
 */
public final class Glob implements Comparable<Glob>, JsonSerializable {
  private final String[] parts;

  private Glob(String... parts) {
    this.parts = parts;
  }

  /**
   * See the grammar at http://code.google.com/p/prebake/wiki/Glob.
   */
  public static Glob fromString(String s) {
    int n = s.length();
    int partCount = 0;
    for (int i = 0; i < n; ++i) {
      ++partCount;
      switch (s.charAt(i)) {
        case '*':  // * or **
          if (i + 1 < n && s.charAt(i + 1) == '*') {
            ++i;
            // Three adjecent **'s not allowed.
            if (i + 1 < n && s.charAt(i + 1) == '*') { badGlob(s); }
          }
          break;
        case '/':
          // Two adjacent path separators not allowed
          if (i != 0 && s.charAt(i - 1) == '/') { badGlob(s); }
          break;
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
    int k = -1;
    for (int i = 0; i < n;) {
      int pos = i;
      switch (s.charAt(i)) {
        case '*':
          if (i + 1 < n && s.charAt(i + 1) == '*') { ++i; }
          break;
        case '/': break;
        default:
          while (i + 1 < n) {
            char next = s.charAt(i + 1);
            if (next == '/') { break; }
            ++i;
          }
          break;
      }
      String part = s.substring(pos, ++i);
      // . and .. are not allowed as path parts.
      switch (part.length()) {
        case 0: throw new IllegalStateException();
        case 2: if ('.' != part.charAt(1)) { break; }
          // $FALL-THROUGH$
        case 1: if ('.' == part.charAt(0)) { badGlob(s); }
      }
      parts[++k] = part;
    }
    return new Glob(parts);
  }

  private static void badGlob(String glob) {
    throw new IllegalArgumentException(glob);
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

  public static Glob intersection(Glob p, Glob q) {
    return new Intersector(p, q).intersection();
  }

  public static boolean overlaps(List<Glob> unionA, List<Glob> unionB) {
    for (Glob a : unionA) {
      for (Glob b : unionB) {
        if (new Intersector(a, b).intersects()) { return true; }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (String part : parts) { sb.append(part); }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Glob && Arrays.equals(this.parts, ((Glob) o).parts);
  }

  @Override
  public int hashCode() { return Arrays.hashCode(parts); }

  public List<String> parts() {
    return Collections.unmodifiableList(Arrays.asList(parts));
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

    Intersector(Glob p, Glob q) {
      this.p = p.parts;
      this.q = q.parts;
      this.m = this.p.length;
      this.n = this.q.length;
    }

    Glob intersection() {
      List<String> partsReversed = inter(0, false, 0, false);
      if (partsReversed == null) { return null; }
      int np = partsReversed.size();
      String[] parts = new String[partsReversed.size()];
      for (int i = np; --i >= 0;) {
        parts[i] = partsReversed.get(np - i - 1);
      }
      return new Glob(parts);
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
          }
          return result;
        }
      }
      return null;
    }
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.writeValue(toString());
  }
}