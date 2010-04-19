package org.prebake.core;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A pattern that matches a group of files.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/Glob">Wiki Docs</a>
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Glob implements Comparable<Glob>, JsonSerializable {
  private final String[] parts;
  private Pattern regex;

  private Glob(String... parts) {
    this.parts = parts;
  }

  /** See the grammar at http://code.google.com/p/prebake/wiki/Glob. */
  public static Glob fromString(String s) {
    int n = s.length();
    int partCount = 0;
    for (int i = 0; i < n; ++i) {
      ++partCount;
      switch (s.charAt(i)) {
        case '*':  // * or **
          if (i + 1 < n && s.charAt(i + 1) == '*') {
            ++i;
            // Three adjacent **'s not allowed.
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
        case 1: if ('.' == part.charAt(0)) { badGlob(s); } break;
      }
      parts[++k] = part;
    }
    return new Glob(parts);
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
    // The literal portions of the output glob.
    // For the output glob "foo/**/bar/baz/*.html", the literal portions are
    // ["foo/", "/bar/baz/", ".html"]
    List<String> literals = Lists.newArrayList();
    // Number of holes in input that don't correspond to any in the output.
    // E.g., for (input="**/foo/*.txt", output="bar/*.txt")
    // which would map { a/foo/x.txt => bar/x.txt, b/c/foo/y.txt => bar/y.txt }
    // nUnusedGroups is 1 since the "**" isn't used in the output.
    final int nUnusedGroups;
    {
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
              + Joiner.on("").join(output.parts, 0, i + 1));
        } else if ("**".equals(inPart) && "*".equals(outPart)) {
          throw new IllegalArgumentException(
              "Can't transform " + input + " to " + output + "."
              + "  There is no corresponding hole for the " + outPart
              + " at the end of " + Joiner.on("").join(output.parts, 0, i + 1));
        }
        literals.add(Joiner.on("").join(
            Arrays.asList(output.parts).subList(i + 1, pos)));
        pos = i;
      }
      literals.add(Joiner.on("").join(
          Arrays.asList(output.parts).subList(0, pos)));
      // Since we iterated above in reverse order.
      Collections.reverse(literals);

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
    final boolean[] slashStarts = new boolean[outputParts.length];
    for (int i = slashStarts.length; --i >= 0;) {
      slashStarts[i] = outputParts[i].startsWith("/");
    }
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
        for (int i = 1; i <= nSubs; ++i) {
          String group = m.group(i + nUnusedGroups);
          if (group != null) { sb.append(group); }
          // Don't double up '/'s.  This might happen for the input
          // foo/**/*.txt and output bar/**/*.txt are transformed against
          // the path foo/a.txt to produce bar/a.txt.  The "**" substitution
          // would match the empty string, leaving the output part, the "/" to
          // run up against the previous literal portion's "/".
          String outputPart = outputParts[i];
          if (slashStarts[i]) {
            int sblen = sb.length();
            if (sblen == 0 || sb.charAt(sblen - 1) == '/') {
              sb.append(outputPart, 1, outputPart.length());
            } else {
              sb.append(outputPart);
            }
          } else {
            sb.append(outputPart);
          }
        }
        return sb.toString();
      }
    };
  }

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

    Intersector(Glob p, Glob q) {
      this.p = p.parts;
      this.q = q.parts;
      this.m = this.p.length;
      this.n = this.q.length;
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

  public boolean match(String path) {
    if (regex == null) {
      StringBuilder sb = new StringBuilder();
      toRegex("/\\\\", false, sb);
      regex = Pattern.compile(sb.toString(), Pattern.DOTALL);
    }
    return regex.matcher(path).matches();
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
              if (nextPart.length() == 2) {
                sb.append("(?:[").append(separatorChars).append("].*)?");
              } else {
                sb.append("(?:[").append(separatorChars).append("][^")
                    .append(separatorChars).append("]*)?");
              }
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
  public static final YSONConverter<List<Glob>> CONV
      = new YSONConverter<List<Glob>>() {
    public @Nullable List<Glob> convert(
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
        out.add(Glob.fromString(glob));
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
            out.add(Glob.fromString(sb.toString()));
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

  public static class GlobSyntaxException extends IllegalArgumentException {
    public GlobSyntaxException(String glob) { super(glob); }
  }
}
