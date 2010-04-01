package org.prebake.js;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JSON parser that generates generic collection classes instead of
 * introducing new collection classes.
 *
 * @author mikesamuel@gmail.com
 */
public final class JsonSource implements Closeable {
  private final Reader in;
  private String pushback;
  private Iterator<String> toks;

  public JsonSource(Reader in) {
    this.in = in;
  }

  public void close() throws IOException {
    this.pushback = null;
    this.toks = Collections.<String>emptyList().iterator();
    in.close();
  }

  public boolean isEmpty() throws IOException {
    if (pushback != null) { return true; }
    tokenize();
    return !toks.hasNext();
  }

  public String next() throws IOException {
    String s = pushback;
    if (s != null) {
      pushback = null;
      return s;
    }
    tokenize();
    if (!toks.hasNext()) { throw new IOException(); }
    return toks.next();
  }

  public Object nextValue() throws IOException {
    String tok = next();
    switch (tok.charAt(0)) {
      case '[': pushback = "["; return nextArray();
      case '{': pushback = "{"; return nextObject();
      case '"': return decodeString(tok);
      case 'f': return Boolean.FALSE;
      case 'n': return null;
      case 't': return Boolean.TRUE;
      default: return Double.valueOf(tok);
    }
  }

  public boolean check(String tok) throws IOException {
    if (isEmpty()) { return false; }
    String t = next();
    if (tok.equals(t)) { return true; }
    pushback = t;
    return false;
  }

  public void expect(String tok) throws IOException {
    String t = next();
    if (!tok.equals(t)) {
      throw new IOException("Expected " + tok + ", but was " + t);
    }
  }

  public String expectString() throws IOException {
    String t = next();
    if (t.charAt(0) == '"') { return decodeString(t); }
    throw new IOException("Expected quoted string, but got " + t);
  }

  private static final Pattern REGEX_TOKENS = Pattern.compile(
      ""
      + "[ \r\n\t\ufeff]+"
      + "|\"(?:\\\\(?:[\"/\\\\bnfrt]|u[0-9a-fA-F]{4})|[^\"\\\\\\r\\n])*\""
      + "|-?\\b(?:[1-9][0-9]*)?[0-9](?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?\\b"
      + "|\\bfalse\\b|\\btrue\\b|\\bnull\\b"
      + "|[,:\\{\\}\\[\\]]");

  private void tokenize() throws IOException {
    if (toks != null) { return; }
    String json;
    {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[1024];
      for (int n; (n = in.read(buf)) >= 0;) {
        sb.append(buf, 0, n);
      }
      in.close();
      json = sb.toString();
    }
    Matcher m = REGEX_TOKENS.matcher(json);
    List<String> toks = Lists.newArrayList();
    int last = 0;
    while (m.find()) {
      String s = m.group();
      if (m.start() != last) {
        throw new IOException(json.substring(last, m.start()));
      }
      last = m.end();
      switch (s.charAt(0)) {
        case ' ': case '\t': case '\r': case '\n': case '\ufeff': continue;
      }
      toks.add(s);
    }
    if (last != json.length()) { throw new IOException(json.substring(last)); }
    this.toks = toks.iterator();
  }

  public static String decodeString(String s) {
    s = s.substring(1, s.length() - 1);
    int n = s.length();
    int esc = s.indexOf('\\');
    if (esc < 0) { return s; }
    StringBuilder sb = new StringBuilder(n);
    int pos = 0;
    do {
      sb.append(s, pos, esc);
      pos = esc + 2;
      char c = s.charAt(esc + 1);
      switch (c) {
        case 'b': sb.append('\b'); break;
        case 'f': sb.append('\f'); break;
        case 'n': sb.append('\n'); break;
        case 'r': sb.append('\r'); break;
        case 't': sb.append('\t'); break;
        case 'u':
          sb.append((char) Integer.parseInt(s.substring(esc + 2, esc + 6), 16));
          pos = esc + 6;
          break;
        default: sb.append(c); break;
      }
    } while ((esc = s.indexOf('\\', pos)) >= 0);
    sb.append(s, pos, n);
    return sb.toString();
  }

  public List<Object> nextArray() throws IOException {
    expect("[");
    List<Object> els = Lists.newArrayList();
    if (!check("]")) {
      do {
        els.add(nextValue());
      } while (check(","));
      expect("]");
    }
    return els;
  }

  public Map<String, Object> nextObject() throws IOException {
    expect("{");
    Map<String, Object> els = Maps.newLinkedHashMap();
    if (!check("}")) {
      do {
        String key = expectString();
        expect(":");
        els.put(key, nextValue());
      } while (check(","));
      expect("}");
    }
    return els;
  }
}
