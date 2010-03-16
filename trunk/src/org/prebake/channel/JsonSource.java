package org.prebake.channel;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      case '[': return nextArray();
      case '{': return nextObject();
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
      + "|\"(?:\\(?:[\"/\\\\bnfrt]|u[0-9a-fA-F]{4})|[^\\\\\"])*\""
      + "|-?(?:[1-9][0-9]*)?[0-9])(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"
      + "|false|true|null"
      + "|[,:\\{\\}\\[\\]]");

  private void tokenize() throws IOException {
    if (toks != null) { return; }
    Matcher m;
    {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[1024];
      for (int n; (n = in.read(buf)) >= 0;) {
        sb.append(buf, 0, n);
      }
      in.close();
      m = REGEX_TOKENS.matcher(sb.toString());
    }
    List<String> toks = new ArrayList<String>();
    while (m.find()) {
      String s = m.group();
      switch (s.charAt(0)) {
        case ' ': case '\t': case '\r': case '\n': case '\ufeff': continue;
      }
      toks.add(s);
    }
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
          sb.append((char) Integer.parseInt(s.substring(esc + 1, esc + 5), 16));
          pos = esc + 5;
          break;
        default: sb.append(c); break;
      }
    } while ((esc = s.indexOf('\\', pos)) >= 0);
    sb.append(s, pos, n);
    return sb.toString();
  }

  public List<Object> nextArray() throws IOException {
    List<Object> els = new ArrayList<Object>();
    if (!check("]")) {
      do {
        els.add(nextValue());
      } while (check(","));
      expect("]");
    }
    return els;
  }

  public Map<String, Object> nextObject() throws IOException {
    Map<String, Object> els = new LinkedHashMap<String, Object>();
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
