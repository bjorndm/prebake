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
