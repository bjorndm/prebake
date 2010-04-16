package org.prebake.js;

%%

%{
  private final StringBuilder sb = new StringBuilder();

  int getPosition() { return yychar; }

  private static Token tok(Token.Type type, String text) {
    return new Token(type, text);
  }
  private static Token tok(Token.Type type) {
    return new Token(type);
  }
%}

%table
%unicode
%char
%line
%column
%eofclose

%type Token
%yylexthrow java.io.IOException

%state STRING
%state BRK

ZERO      = [-]?[0]
INT       = [-]?[1-9][0-9]*
REAL      = [-]?([1-9][0-9]*|0)(\.[0-9]+)?([eE][+-]?[0-9]+)?
WS        = [ \t\r\n]+
STR_CHAR  = [^\"\\]
STR_ESC   = \\[^\r\n]
OTHER     = .
%%

<STRING> \"            {
  yybegin(YYINITIAL);
  return tok(Token.Type.STR, sb.append('"').toString());
}
<STRING> {STR_CHAR}+   {sb.append(yytext());}
<STRING> {STR_ESC}     {sb.append(yytext());}
<YYINITIAL> \"         {yybegin(STRING); sb.setLength(0); sb.append('"');}
<YYINITIAL> {REAL}     {yybegin(BRK); return tok(Token.Type.NUM, yytext());}
<YYINITIAL> {INT}      {yybegin(BRK); return tok(Token.Type.NUM, yytext());}
<YYINITIAL> {ZERO}     {yybegin(BRK); return tok(Token.Type.NUM, yytext());}
<YYINITIAL> "true"     {yybegin(BRK); return tok(Token.Type.TRUE);}
<YYINITIAL> "false"    {yybegin(BRK); return tok(Token.Type.FALSE);}
<YYINITIAL> "null"     {yybegin(BRK); return tok(Token.Type.NULL);}
<YYINITIAL> "{"        {return tok(Token.Type.LCURLY);}
<YYINITIAL> "}"        {return tok(Token.Type.RCURLY);}
<YYINITIAL> "["        {return tok(Token.Type.LSQUARE);}
<YYINITIAL> "]"        {return tok(Token.Type.RSQUARE);}
<YYINITIAL> ","        {return tok(Token.Type.COMMA);}
<YYINITIAL> ":"        {return tok(Token.Type.COLON);}
<YYINITIAL> {WS}       {/* ignorable */}
<YYINITIAL> {OTHER}    {
  throw new java.io.IOException(
      "Malformed json at " + yyline + "+"
      + yycolumn + " : " + yycharat(0));
}
// Above we switch to BRK after any token that should not be followed by a
// letter or digit.  Switch back to YYINITIAL once we've seen a punctuation
// or space token.
<BRK>        \"        {yybegin(STRING); sb.setLength(0); sb.append('"');}
<BRK>       "{"        {yybegin(YYINITIAL); return tok(Token.Type.LCURLY);}
<BRK>       "}"        {yybegin(YYINITIAL); return tok(Token.Type.RCURLY);}
<BRK>       "["        {yybegin(YYINITIAL); return tok(Token.Type.LSQUARE);}
<BRK>       "]"        {yybegin(YYINITIAL); return tok(Token.Type.RSQUARE);}
<BRK>       ","        {yybegin(YYINITIAL); return tok(Token.Type.COMMA);}
<BRK>       ":"        {yybegin(YYINITIAL); return tok(Token.Type.COLON);}
<BRK>       {WS}       {yybegin(YYINITIAL);}
<BRK>       {OTHER}    {
  throw new java.io.IOException(
      "Malformed json at " + yyline + "+"
      + yycolumn + " : " + yycharat(0));
}
