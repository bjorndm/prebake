package org.prebake.js;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;

/**
 * A FireBug-like <tt>console</tt> interface for JavaScript.
 *
 * <p>
 * This is public so that the JS executor can find methods reflectively.
 * It is not meant to be used outside this package otherwise.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/JsConsole">wiki</a>
 * @author mikesamuel@gmail.com
 */
public final class Console {
  private final Logger logger;
  private final List<Group> groups = Lists.newArrayList();
  private final Map<String, Long> timers = Maps.newHashMap();

  private static final Pattern STACK_FRAME = Pattern.compile(
      "^\tat ([^:]+):(\\d+)(?: \\(([^)]+)\\))?", Pattern.MULTILINE);

  Console(Logger logger) {
    this.logger = logger;
  }

  private static boolean requiresFloatingPoint(char ch) {
    switch (ch) {
      case 'e': case 'E': case 'f': case 'g': case 'G': case 'a': case 'A':
        return true;
      default:
        return false;
    }
  }

  private static final Pattern FORMAT_SPECIFIER = Pattern.compile(
      "%(?:(?:\\d+\\$)?(?:[\\-#+ 0,(]+)?(?:\\d+)?(?:\\.\\d+)?([a-zA-Z])|%)");

  private void log(Level level, String format, Object... args) {
    char[] fmtChars = null;
    for (int i = args.length; --i >= 0;) {
      Object o = args[i];
      if (o instanceof Double
          && ((Double) o).doubleValue() == ((Double) o).longValue()) {
        // Convert Doubles to Longs when doing so does not lose information.
        // This solves the problem of console.log("%s", 1) logging "1.0",
        // and fixes "%d".
        if (fmtChars == null) {
          fmtChars = new char[args.length];
          Matcher m = FORMAT_SPECIFIER.matcher(format);
          int f = 0;
          while (m.find() && f < fmtChars.length) {
            String s = m.group(1);
            if (s != null) { fmtChars[f++] = s.charAt(0); }
          }
          if (requiresFloatingPoint(fmtChars[i])) { continue; }
          args = args.clone();
        } else if (requiresFloatingPoint(fmtChars[i])) {
          continue;
        }
        args[i] = Long.valueOf(((Double) o).longValue());
      }
    }
    StringBuilder sb = new StringBuilder();
    int nSpaces = groups.size() * 2;
    while (nSpaces >= 16) { sb.append(SIXTEEN_SPACES); nSpaces -= 16; }
    sb.append(SIXTEEN_SPACES, 0, nSpaces);
    Formatter f = new Formatter(sb /*, default Locale */);
    f.format(format, args);
    LogRecord lr = new LogRecord(level, sb.toString());
    Matcher m = STACK_FRAME.matcher(
        new EvaluatorException(null).getScriptStackTrace());
    if (m.find()) {
      String file = m.group(1);
      String line = m.group(2);
      String fnName = m.group(3);
      lr.setSourceClassName(file + ":" + line);
      lr.setSourceMethodName(fnName != null ? fnName : "<anonymous>");
    }
    logger.log(lr);
  }

  public void log(String format, Object... args) {
    log(Level.INFO, format, args);
  }

  public void warn(String format, Object... args) {
    log(Level.WARNING, format, args);
  }

  public void error(String format, Object... args) {
    log(Level.SEVERE, format, args);
  }

  public void info(String format, Object... args) {
    log(Level.FINE, format, args);
  }

  public void debug(String format, Object... args) {
    log(Level.INFO, format, args);
  }

  public void assert_(Object truth) { assert_(truth, null); }

  public void assert_(Object truth, Object message) {
    if (!Context.toBoolean(truth)) {
      if (message == null) { message = "Assertion Failure"; }
      String messageStr = message.toString();
      error(messageStr);
      throw new JavaScriptException(messageStr, "console", 1);
    }
  }

  public void dir(Object obj) {
    List<String> pairs = Lists.newArrayList("Name", "Value");
    if (obj instanceof Scriptable) {
      Scriptable s = (Scriptable) obj;
      for (Object id : s.getIds()) {
        if (id instanceof Number) {
          pairs.add("" + id);
          pairs.add(valueToStr(s.get(((Number) id).intValue(), s)));
        } else {
          String idStr = (String) id;
          pairs.add(idStr);
          pairs.add(valueToStr(s.get(idStr, s)));
        }
      }
    }
    log(Level.INFO, toTable(pairs, 2));
  }

  public void group(String name) {
    log(Level.INFO, "Enter " + name);
    groups.add(new Group(name));
  }

  public void groupEnd() {
    log(Level.INFO, "Exit  " + groups.remove(0).name);
  }

  public void time(String name) {
    timers.put(name, System.nanoTime());
  }

  public void timeEnd(String name) {
    Long t0 = timers.remove(name);
    if (t0 != null) {
      long t1 = System.nanoTime();
      log(Level.INFO, "Timer %s took %sns", name, t1 - t0);
    }
  }

  public void trace() {
    log(Level.INFO, new EvaluatorException(null).getScriptStackTrace());
  }

  // TODO: make assert work as assert_
  // TODO: profile, profileEnd, count

  private static final String SIXTEEN_SPACES = "                ";
  static { assert 16 == SIXTEEN_SPACES.length(); }

  private static String valueToStr(Object o) {
    if (o == null) { return "null"; }
    if (o.getClass().isArray()) {
      if (o instanceof Object[]) { return Arrays.toString((Object[]) o); }
      Class<?> cl = o.getClass().getComponentType();
      if (cl == Byte.TYPE) { return Arrays.toString((byte[]) o); }
      if (cl == Character.TYPE) { return Arrays.toString((char[]) o); }
      if (cl == Double.TYPE) { return Arrays.toString((double[]) o); }
      if (cl == Float.TYPE) { return Arrays.toString((float[]) o); }
      if (cl == Integer.TYPE) { return Arrays.toString((int[]) o); }
      if (cl == Long.TYPE) { return Arrays.toString((long[]) o); }
      if (cl == Short.TYPE) { return Arrays.toString((short[]) o); }
    }
    return Context.toString(o);
  }

  private static String toTable(List<? extends String> cells, int n) {
    int m = cells.size() / n;
    int[] maxLens = new int[n];
    for (int i = 0; i < m * n; ++i) {
      maxLens[i % n] = Math.max(maxLens[i % n], cells.get(i).length());
    }
    int rowLen = 3 * n;  // n + 1 dividers, 2(n - 1) spaces, and 1 newline.
    for (int len : maxLens) { rowLen += len; }
    StringBuilder sb = new StringBuilder(rowLen * m);
    for (int k = 0; k < m * n;) {
      sb.append("\n|");
      for (int j = 0; j < n; ++j, ++k) {
        sb.append(' ');
        String s = cells.get(k);
        sb.append(s);
        int padding = maxLens[j] - s.length();
        while (padding >= 16) {
          sb.append(SIXTEEN_SPACES);
          padding -= 16;
        }
        sb.append(SIXTEEN_SPACES, 0, padding);
        sb.append(" |");
      }
    }
    return sb.toString();
  }

  private static final class Group {
    final String name;
    Group(String name) { this.name = name; }
  }
}
