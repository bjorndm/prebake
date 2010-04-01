package org.prebake.js;

import org.prebake.core.MessageQueue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.KeywordLiteral;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;

/**
 * Extends JSON to allow for functions with no free variables.
 * The letter Y is a crude transliteration of &lambda; into the Roman alphabet.
 *
 * @author mikesamuel@gmail.com
 */
public final class YSON {
  private final AstNode root;
  private YSON(AstNode root) { this.root = root; }

  public Set<String> getFreeNames() {
    // The free names of a production P are union(RN(P), LSD(P), VSD(P))
    Set<String> fn = Sets.newLinkedHashSet();
    rn(root, fn);
    vsd(root, fn);
    return fn;
  }

  Set<String> getRequiredNames() {
    Set<String> rn = Sets.newLinkedHashSet();
    rn(root, rn);
    return rn;
  }

  Set<String> getVarScopeDeclarations() {
    Set<String> vsd = Sets.newLinkedHashSet();
    vsd(root, vsd);
    return vsd;
  }

  /** Makes a best effort to convert to JavaScript source code. */
  public String toSource() { return root.toSource(); }

  /**
   * Converts the JavaScript values in a YSON representation into Java objects.
   * JavaScript arrays are coerced to {@link java.util.List}s, object
   * constructors are converted to {@link java.util.Map}s, JavaScript primitives
   * are converted to the corresponding Java primitive wrapper types, JavaScript
   * functions are converted to {@link Lambda}s, and other values are converted
   * to {@code null}.
   */
  public Object toJavaObject() {
    return YSON.toJavaObject(root);
  }

  public static final Set<String> DEFAULT_YSON_ALLOWED = ImmutableSet.of(
      "Array",
      "Boolean",
      "Date",
      "Error",
      "EvalError",
      "Function",
      "Infinity",
      "JSON",
      "Math",
      "NaN",
      "Number",
      "Object",
      "RangeError",
      "ReferenceError",
      "RegExp",
      "String",
      "SyntaxError",
      "TypeError",
      "URIError",
      "decodeURIComponent",
      "encodeURIComponent",
      "eval",
      "isFinite",
      "isNaN",
      "parseFloat",
      "parseInt",
      "undefined"
      );

  public static boolean isYSON(
      String js, Set<String> allowedFreeVars, MessageQueue mq) {
    return requireYSON(js, allowedFreeVars, mq) != null;
  }

  public static YSON requireYSON(
      String js, Set<String> allowedFreeVars, MessageQueue mq) {
    if (js == null) {
      if (mq != null) { mq.getMessages().add("Output is null"); }
      return null;
    }
    YSON yson;
    try {
      yson = parseExpr(js);
    } catch (ParseException ex) {
      if (mq != null) { mq.getMessages().add(ex.getMessage()); }
      return null;
    }

    return requireYSON(yson, allowedFreeVars, mq);
  }

  public static YSON requireYSON(
      YSON yson, Set<String> allowedFreeVars, MessageQueue mq) {
    Set<String> freeNames = yson.getFreeNames();
    freeNames.removeAll(allowedFreeVars);
    if (!freeNames.isEmpty()) {
      if (mq != null) {
        mq.getMessages().add(
            "Disallowed free variables: " + Joiner.on(", ").join(freeNames));
      }
      return null;
    }
    return isStructuralYSON(yson.root, mq) ? yson : null;
  }

  private static boolean isStructuralYSON(
      final AstNode node, final MessageQueue mq) {
    switch (node.getType()) {
      case Token.FALSE: case Token.FUNCTION: case Token.NULL:
      case Token.NUMBER: case Token.STRING: case Token.TRUE:
      case Token.NEG: case Token.POS:
        return true;
      case Token.ARRAYLIT: case Token.EXPR_RESULT: case Token.LP:
      case Token.OBJECTLIT: case Token.SCRIPT:
        final boolean[] result = new boolean[] { true };
        node.visit(new NodeVisitor() {
          public boolean visit(AstNode n) {
            if (n == node) { return true; }
            if (!isStructuralYSON(n, mq)) { result[0] = false; }
            return false;
          }
        });
        return result[0];
      case Token.COLON:
        return isStructuralYSON(((ObjectProperty) node).getRight(), mq);
      case Token.EMPTY:
        if (mq != null) {
          mq.getMessages().add("Not YSON: trailing commas or empty expression");
        }
        return false;
      default:
        if (mq != null) {
          mq.getMessages().add("Not YSON: " + node.toSource());
        }
        return false;
    }
  }

  public static YSON parse(String js) throws ParseException {
    try {
      return new YSON(new Parser().parse(js, "YSON", 1));
    } catch (EvaluatorException ex) {
      throw new ParseException(ex.details() + " in " + abbrev(js), -1);
    }
  }

  private static String abbrev(String s) {
    return (s.length() < 40 ? s : s.substring(0, 37) + "...")
        .replaceAll("[\r\n]+", " ");
  }

  private static final Pattern AMBIGUOUS_START = Pattern.compile(
      ""
      + "^(?:"
        // line continuation
        + "\\\\(?:\r\n?|[\n\u2028\u2029])"
        // whitespace
        + "|[\\ufeff\\s]+"
        // line comment
        + "|//[^\r\n\u2028\u2029]"
        // block comment
        + "|/\\*.*?\\*/"
      + ")*"
      // The keyword function or an open curly brace
      + "(?:\\{|function[^\\w$_])");
  public static YSON parseExpr(String js) throws ParseException {
    // If the input could be confused with a block or function declaration,
    // parenthesize it.  Don't always do this because that could let some
    // illegal inputs through (e.g. "1) + (1", and produces more confusing
    // error messages.
    // TokenStream not public so figure out whether it starts with an ambiguous
    // production using a regex.
    if (AMBIGUOUS_START.matcher(js).find()) { js = "(" + js + ")"; }
    YSON yson = parse(js);
    if (yson.root instanceof ScriptNode) {
      ScriptNode n = (ScriptNode) yson.root;
      Iterator<Node> children = n.iterator();
      if (children.hasNext()) {
        Node child = children.next();
        if (!children.hasNext()) {
          if (child instanceof ExpressionStatement) {
            child = ((ExpressionStatement) child).getExpression();
            return new YSON((AstNode) child);
          }
        }
      }
    }
    return yson;
  }

  private static String propertyKeyToString(AstNode key) {
    if (key instanceof Name) {
      return ((Name) key).getIdentifier();
    } else if (key instanceof StringLiteral) {
      return ((StringLiteral) key).getValue();
    } else {
      return key.toSource(0);
    }
  }

  private static void rn(AstNode node, Set<String> names) {
    switch (node.getType()) {
      case Token.BREAK: case Token.CONTINUE: break;
      case Token.CATCH: {
        CatchClause cc = (CatchClause) node;
        Set<String> rnBody = Sets.newHashSet();
        rn(cc.getBody(), rnBody);
        rnBody.remove(cc.getVarName().getIdentifier());
        names.addAll(rnBody);
        return;
      }
      case Token.CONST: case Token.VAR:
        for (VariableInitializer v
             : ((VariableDeclaration) node).getVariables()) {
          AstNode init = v.getInitializer();
          if (init != null) { rn(init, names); }
        }
        return;
      case Token.GETPROP: rn(((PropertyGet) node).getTarget(), names); return;
      case Token.FUNCTION: {
        Set<String> declared = Sets.newHashSet();
        declared.add("this");
        declared.add("arguments");
        FunctionNode fn = (FunctionNode) node;
        Name name = fn.getFunctionName();
        if (name != null) {
          declared.add(name.getIdentifier());
        }
        for (AstNode param : fn.getParams()) {
          declared.add(((Name) param).getIdentifier());
        }
        vsdAll(fn.getBody(), declared);
        Set<String> rnBody = Sets.newLinkedHashSet();
        rnAll(fn.getBody(), rnBody);
        rnBody.removeAll(declared);
        names.addAll(rnBody);
        return;
      }
      case Token.COLON:
        if (node instanceof ObjectProperty) {
          rn(((ObjectProperty) node).getRight(), names);
        }
        break;
      case Token.NAME: names.add(((Name) node).getIdentifier()); break;
      case Token.THIS: names.add("this"); break;
      default: rnAll(node, names); break;
    }
  }

  private static void rnAll(final AstNode node, final Set<String> names) {
    node.visit(new NodeVisitor() {
      public boolean visit(AstNode n) {
        if (n == node) { return true; }
        rn(n, names);
        return false;
      }
    });
  }

  private static void vsd(AstNode node, Set<String> names) {
    switch (node.getType()) {
      case Token.BREAK: case Token.CONTINUE:
      case Token.EXPR_RESULT: case Token.EXPR_VOID:
        break;
      case Token.CONST: case Token.VAR:
        for (VariableInitializer v
             : ((VariableDeclaration) node).getVariables()) {
          names.add(((Name) v.getTarget()).getIdentifier());
        }
        break;
      case Token.FUNCTION:
        FunctionNode fn = (FunctionNode) node;
        Name name = fn.getFunctionName();
        if (name != null && name.getDefiningScope() != node) {
          names.add(name.getIdentifier());
        }
        break;
      default:
        vsdAll(node, names);
        break;
    }
  }

  private static void vsdAll(final AstNode node, final Set<String> names) {
    node.visit(new NodeVisitor() {
      public boolean visit(AstNode n) {
        if (n == node) { return true; }
        vsd(n, names);
        return false;
      }
    });
  }

  private static Object toJavaObject(AstNode node) {
    if (node instanceof StringLiteral) {
      return ((StringLiteral) node).getValue();
    } else if (node instanceof NumberLiteral) {
      return ((NumberLiteral) node).getDouble();
    } else if (node instanceof FunctionNode) {
      return new Lambda(node.toSource());
    } else if (node instanceof KeywordLiteral) {
      switch (((KeywordLiteral) node).getType()) {
        case Token.TRUE: return Boolean.TRUE;
        case Token.FALSE: return Boolean.FALSE;
        default: return null;
      }
    } else if (node instanceof ObjectLiteral) {
      Map<String, Object> m = Maps.newLinkedHashMap();
      boolean hasNull = false;
      for (ObjectProperty prop: ((ObjectLiteral) node).getElements()) {
        String key = propertyKeyToString(prop.getLeft());
        Object value = toJavaObject(prop.getRight());
        m.put(key, value);
        if (value == null) { hasNull = true; }
      }
      return hasNull ? Collections.unmodifiableMap(m) : ImmutableMap.copyOf(m);
    } else if (node instanceof ArrayLiteral) {
      List<Object> els = Lists.newArrayList();
      for (AstNode el : ((ArrayLiteral) node).getElements()) {
        els.add(toJavaObject(el));
      }
      return els.contains(null)
          ? Collections.unmodifiableList(els) : ImmutableList.copyOf(els);
    } else if (node instanceof ScriptNode) {
      Iterator<Node> children = ((ScriptNode) node).iterator();
      if (children.hasNext()) {
        return toJavaObject((AstNode) children.next());
      }
      return null;
    } else if (node instanceof UnaryExpression) {
      UnaryExpression e = (UnaryExpression) node;
      switch (e.getType()) {
        case Token.NEG: {
          Object value = toJavaObject(e.getOperand());
          return value instanceof Number
              ? -((Number) value).doubleValue() : Double.NaN;
        }
        case Token.POS: {
          Object value = toJavaObject(e.getOperand());
          return value instanceof Number
              ? ((Number) value).doubleValue() : Double.NaN;
        }
        default: return null;
      }
    } else if (node instanceof ExpressionStatement) {
      return toJavaObject(((ExpressionStatement) node).getExpression());
    } else if (node instanceof ParenthesizedExpression) {
      return toJavaObject(((ParenthesizedExpression) node).getExpression());
    } else {
      return null;
    }
  }

  public static final class Lambda implements JsonSerializable {
    private final String source;

    public Lambda(String source) {
      assert source != null && source.startsWith("function")
          && source.endsWith("}") : source;
      this.source = source;
    }

    public String getSource() { return source; }

    @Override
    public String toString() { return source; }

    @Override
    public boolean equals(Object o) {
      return o instanceof Lambda && source.equals(((Lambda) o).source);
    }

    @Override
    public int hashCode() { return source.hashCode(); }

    public void toJson(JsonSink sink) throws IOException { sink.write(source); }
  }
}