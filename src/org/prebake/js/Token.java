package org.prebake.js;

import javax.annotation.Nullable;

final class Token {
  public final Type type;
  public final String text;

  Token(Type type, String text) {
    this.type = type;
    this.text = text;
  }

  Token(Type type) { this(type, type.text); }

  @Override public String toString() { return "(" + type + " " + text + ")"; }

  enum Type {
    COLON(":"),
    COMMA(","),
    LCURLY("{"),
    RCURLY("}"),
    LSQUARE("["),
    RSQUARE("]"),
    NULL("null"),
    FALSE("false"),
    TRUE("true"),
    NUM,
    STR,
    ;

    final String text;
    Type() { this(null); }
    Type(@Nullable String text) { this.text = text; }
  }
}
