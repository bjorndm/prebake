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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A JSON parser that generates generic collection classes instead of
 * introducing new collection classes.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class JsonSource implements Closeable {
  private final Reader in;
  private Token pushback;
  private Iterator<Token> toks;

  public JsonSource(Reader in) {
    this.in = in;
  }

  public void close() throws IOException {
    this.pushback = null;
    this.toks = Collections.<Token>emptyList().iterator();
    in.close();
  }

  public boolean isEmpty() throws IOException {
    if (pushback != null) { return true; }
    tokenize();
    return !toks.hasNext();
  }

  public @Nonnull String next() throws IOException {
    return nextToken().text;
  }

  public @Nonnull Token nextToken() throws IOException {
    Token t = pushback;
    if (t != null) {
      pushback = null;
      return t;
    }
    tokenize();
    if (!toks.hasNext()) { throw new IOException(); }
    return toks.next();
  }

  public @Nullable Object nextValue() throws IOException {
    Token tok = nextToken();
    switch (tok.type) {
      case LSQUARE: pushback = tok; return nextArray();
      case LCURLY: pushback = tok; return nextObject();
      case STR: return decodeString(tok.text);
      case FALSE: return Boolean.FALSE;
      case NULL: return null;
      case TRUE: return Boolean.TRUE;
      default:
        String numStr = tok.text;
        boolean nonZero = false;
        for (int i = numStr.length(); --i >= 0;) {
          switch (numStr.charAt(i)) {
            case '1': case '2': case '3': case '4': case '5':
            case '6': case '7': case '8': case '9':
              nonZero = true;
              break;
            case 'e': case 'E': case '.':
              return Double.valueOf(numStr);
          }
        }
        if (!nonZero && numStr.charAt(0) == '-') {
          // Properly deal with negative zero.
          return Double.valueOf(numStr);
        }
        // Avoid rounding error for large integral constants.
        return Long.valueOf(numStr);
    }
  }

  public boolean check(String s) throws IOException {
    if (isEmpty()) { return false; }
    Token tok = nextToken();
    if (tok.text.equals(s)) { return true; }
    pushback = tok;
    return false;
  }

  private boolean check(Token.Type type) throws IOException {
    if (isEmpty()) { return false; }
    Token tok = nextToken();
    if (tok.type == type) { return true; }
    pushback = tok;
    return false;
  }

  public void expect(String tok) throws IOException {
    String t = next();
    if (!tok.equals(t)) {
      throw new IOException("Expected " + tok + ", but was " + t);
    }
  }

  private void expect(Token.Type type) throws IOException {
    Token t = nextToken();
    if (type != t.type) {
      throw new IOException("Expected " + type.text + ", but was " + t.text);
    }
  }

  public @Nonnull String expectString() throws IOException {
    String t = next();
    if (t.charAt(0) == '"') { return decodeString(t); }
    throw new IOException("Expected quoted string, but got " + t);
  }

  private void tokenize() throws IOException {
    if (toks != null) { return; }

    List<Token> toks = Lists.newArrayList();
    Yylex lexer = new Yylex(in);
    for (Token tok; (tok = lexer.yylex()) != null;) {
      toks.add(tok);
    }
    this.toks = toks.iterator();
  }

  public static @Nonnull String decodeString(String s) {
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

  public @Nonnull List<Object> nextArray() throws IOException {
    expect(Token.Type.LSQUARE);
    List<Object> els = Lists.newArrayList();
    if (!check(Token.Type.RSQUARE)) {
      do {
        els.add(nextValue());
      } while (check(Token.Type.COMMA));
      expect(Token.Type.RSQUARE);
    }
    return els;
  }

  public @Nonnull Map<String, Object> nextObject() throws IOException {
    expect(Token.Type.LCURLY);
    Map<String, Object> els = Maps.newLinkedHashMap();
    if (!check(Token.Type.RCURLY)) {
      do {
        String key = expectString();
        expect(Token.Type.COLON);
        els.put(key, nextValue());
      } while (check(Token.Type.COMMA));
      expect(Token.Type.RCURLY);
    }
    return els;
  }
}
