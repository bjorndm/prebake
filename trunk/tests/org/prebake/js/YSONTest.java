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

import org.prebake.core.MessageQueue;
import org.prebake.util.PbTestCase;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class YSONTest extends PbTestCase {
  @Test public final void testFreeVars() throws Exception {
    assertFreeVars("function () {}");
    assertFreeVars("(function x() {})");
    assertFreeVars("(function x(y) { return y == 1 ? 1 : y * x(y - 1); })");
    assertFreeVars(
        "(function x(y) { return y == 1 ? z : y * x(y - 1); })", "z");
    assertFreeVars(
        "(function x(y) { var z; return y == 1 ? z : y * x(y - 1); })");
    assertFreeVars("(function x(y) { return e; })", "e");
    assertFreeVars(
        "(function x(y) { try { foo(); } catch (e) { return e; } })", "foo");
    assertFreeVars(
        "(function x(y) { try { foo(); } catch (e) {} return e; })",
        "foo", "e");
    assertFreeVars("this", "this");
    assertFreeVars("eval", "eval");
    assertFreeVars("function () { return function () { return x; } }", "x");
  }

  @Test public final void testAstInfo() throws ParseException {
    withCode("0").run();
    withCode("x").rn("x").fn("x").run();
    withCode("var x;").vsd("x").fn("x").run();
    withCode("var x, y = z;").vsd("x", "y").rn("z").fn("x", "y", "z").run();
    withCode("x(); { var x; }").vsd("x").rn("x").fn("x").run();
    withCode("(function f(a) { var b; return a + b; })").run();
    withCode("function f(a) { var b; return a + b; }").fn("f").vsd("f").run();
    withCode("function f(a) { function g(b) { return a + b + c; }; return g; }")
        .rn("c").fn("c", "f").vsd("f").run();
    withCode("for (var i = 0, o; o = arr[i]; ++i) { foo(o); }").vsd("i", "o")
        .rn("arr", "foo", "i", "o").fn("arr", "foo", "i", "o").run();
    withCode(
        "(function (arr) { for (var i = 0, o; o = arr[i]; ++i) { foo(o); } })()"
        ).rn("foo").fn("foo").run();
    withCode(""
             + "(function a(b, c) {"
             + " var d; function e(f) { var g; } return [a,b,c,d,e,f,g,h];"
             + "})")
        .rn("f", "g", "h").fn("f", "g", "h").run();
    withCode("var a = function a(b, c) {}, d = e;").vsd("a", "d").rn("e")
        .fn("a", "d", "e").run();
    withCode("for (var k in o) { foo(o[k]); }").vsd("k").rn("foo", "k", "o")
        .fn("foo", "k", "o").run();
    withCode("for (k in o) { foo(o[k]); }").rn("foo", "k", "o")
        .fn("foo", "k", "o").run();
    withCode("try { f(e); } catch (e) { g(e); }").rn("e", "f", "g")
        .fn("e", "f", "g").run();
    withCode("try { f(h); } catch (e) { g(e); }").rn("f", "g", "h")
        .fn("f", "g", "h").run();
    withCode("try { f(h); } catch (e) { g(e); } finally { g(e); }")
        .rn("e", "f", "g", "h").fn("e", "f", "g", "h").run();
    withCode("try { f(h); } catch (e) { var x = g(e); }").vsd("x")
        .rn("f", "g", "h").fn("f", "g", "h", "x").run();
    withCode("try { f(h); } catch (e) { var e = g(e); }").vsd("e")
        .rn("f", "g", "h").fn("e", "f", "g", "h").run();
    withCode("try { f(h); } catch (e) { g(e); } finally { var e = g(e); }")
        .vsd("e").rn("f", "g", "h", "e").fn("e", "f", "g", "h").run();
    withCode(""
             + "(function () {"
             + "  return [this, that, arguments, argumentative, null,"
             + "           undefined];"
             + "})")
        .rn("argumentative", "that", "undefined")
        .fn("argumentative", "that", "undefined").run();
    withCode("[this, that, arguments, argumentative, null, undefined]")
        .rn("argumentative", "arguments", "that", "this", "undefined")
        .fn("argumentative", "arguments", "that", "this", "undefined").run();
    withCode("bar: while (1) { break bar; }").run();
    withCode("bar: while (1) { continue bar; }").run();
    withCode("bar: while (1) { break; }").run();
    withCode("bar: while (1) { continue; }").run();
    withCode("bar: do { break bar; } while (1)").run();
    withCode("bar: do { continue bar; } while (1)").run();
    withCode("bar: do { break; } while (1)").run();
    withCode("bar: do { continue; } while (1)").run();
    withCode("bar: for (;;) { break bar; }").run();
    withCode("bar: for (;;) { continue bar; }").run();
    withCode("bar: for (;;) { break; }").run();
    withCode("bar: for (;;) { continue; }").run();
    withCode("bar: switch (x) { default: break bar; }").rn("x").fn("x").run();
    withCode("bar: switch (x) { default: break; }").rn("x").fn("x").run();
    withCode("do { break; } while (1)").run();
    withCode("do { continue; } while (1)").run();
    withCode("(function () { return })()").run();
    withCode("(function () { return bar })()").rn("bar").fn("bar").run();
    withCode("eval('alert(\"foo\")')").rn("eval").fn("eval").run();
    withCode("eval('alert(\"foo\")', bar)").rn("bar", "eval").fn("bar", "eval")
        .run();
    withCode("a[b] + c.d").rn("a", "b", "c").fn("a", "b", "c").run();
  }

  @Test public final void testIsYSON() {
    assertNotYson("foo bar", "missing ; before statement in foo bar");
    assertYson("{ a: 1 }");
    assertYson("[1,2,3]");
    assertNotYson("[1,2,3,,]", "Not YSON: trailing commas or empty expression");
    assertYson(" \"foo\" ");
    assertYson(" 0.5 ");
    assertYson(" -0.5 ");
    assertYson(" +1e2 ");
    assertYson(" [true, false, null] ");
    assertNotYson("NaN", "Not YSON: NaN");
    assertNotYson("this", "Disallowed free variables: this");
    assertNotYson("undefined", "Not YSON: undefined");
    assertYson("{ 'ok': function (x, y) { return x * y } }");
    assertNotYson(
        "{ 'ok': function (x) { return x * y } }",
        "Disallowed free variables: y");
    assertNotYson(
        "{ foo: 0 }), ({ bar: 4 }", "Not YSON: ({foo: 0}) , ({bar: 4})");
  }

  @Test public final void testToJava() throws Exception {
    assertEquals(
        Arrays.asList("foo", "bar"),
        YSON.parseExpr("['foo', 'bar']").toJavaObject());
    assertEquals(Boolean.TRUE, YSON.parseExpr("true").toJavaObject());
    assertEquals(Boolean.FALSE, YSON.parseExpr("false").toJavaObject());
    assertEquals(-2.0, YSON.parseExpr("-2").toJavaObject());
    assertEquals(null, YSON.parseExpr("null").toJavaObject());
    Map<String, Object> golden = Maps.newLinkedHashMap();
    golden.put("foo", "bar");
    golden.put("bAz", ImmutableList.of("boo"));
    golden.put("get", 3.0);
    golden.put("3", null);
    Map<?, ?> actual = (Map<?, ?>) YSON.parse(
        "({ foo: 'bar', 'b\\x41z': ['boo'], get: 3, 3: null })")
        .toJavaObject();
    assertEquals(golden, actual);
    assertEquals(  // iteration order preserved
        Lists.newArrayList(golden.keySet()),
        Lists.newArrayList(actual.keySet()));
  }

  @Test public final void testIsValidIdentifier() {
    assertFalse(YSON.isValidIdentifier(""));
    assertTrue(YSON.isValidIdentifier("x"));
    assertTrue(YSON.isValidIdentifier("x1"));
    assertFalse(YSON.isValidIdentifier("1"));
    assertFalse(YSON.isValidIdentifier("this"));
    assertFalse(YSON.isValidIdentifier("null"));
    assertTrue(YSON.isValidIdentifier("\u00c5"));
    assertFalse(YSON.isValidIdentifier("\u0041\u030a"));
    assertFalse(YSON.isValidIdentifier("\"x\""));
    assertFalse(YSON.isValidIdentifier("a-b"));
  }

  @Test public final void testRequireJsonKeyFilteringNoFreeVars()
      throws Exception {
    String js = "{ y: 1, x: 2, z: { x: 3 } }";

    assertFilteredYson(js, false, false, "{\"y\":1,\"x\":2,\"z\":{\"x\":3}}");
    assertFilteredYson(js, false, true, "{\"y\":1,\"z\":{\"x\":3}}");
    assertFilteredYson(js, true, false, "{\"y\":1,\"x\":2,\"z\":{\"x\":3}}");
    assertFilteredYson(js, true, true, "{\"y\":1,\"z\":{\"x\":3}}");
  }

  @Test public final void testRequireJsonKeyFilteringOuterFreeVar()
      throws Exception {
    String js = "{ y: 1, x: function () { return x }, z: { x: 3 } }";

    assertFilteredYson(js, false, false, null, "Disallowed free variables: x");
    assertFilteredYson(js, false, true, "{\"y\":1,\"z\":{\"x\":3}}");
    assertFilteredYson(
        js, true, false,
        "{\"y\":1,\"x\":function() {\n  return x;\n},\"z\":{\"x\":3}}");
    assertFilteredYson(js, true, true, "{\"y\":1,\"z\":{\"x\":3}}");
  }

  @Test public final void testRequireJsonKeyFilteringInnerFreeVar()
      throws Exception {
    String js = "{ y: 1, x: 2, z: { x: function () { return x } } }";

    assertFilteredYson(js, false, false, null, "Disallowed free variables: x");
    assertFilteredYson(js, false, true, null, "Disallowed free variables: x");
    assertFilteredYson(
        js, true, false,
        "{\"y\":1,\"x\":2,\"z\":{\"x\":function() {\n  return x;\n}}}");
    assertFilteredYson(
        js, true, true, "{\"y\":1,\"z\":{\"x\":function() {\n  return x;\n}}}");
  }

  private static final class IsNotX implements Predicate<String> {
    public boolean apply(String s) { return !"x".equals(s); }
  }

  private void assertFilteredYson(
      String js, boolean allowX, boolean filterX, @Nullable String golden,
      String... messages) throws Exception {
    MessageQueue mq = new MessageQueue();
    YSON yson = YSON.parseExpr(js);
    if (filterX) {
      yson = yson.filter(new IsNotX());
    }
    YSON out = YSON.requireYSON(
        yson,
        allowX ? Collections.singleton("x") : Collections.<String>emptySet(),
        mq);
    String actual = out != null ? JsonSink.stringify(out.toJavaObject()) : null;
    assertEquals(golden, actual);
    assertEquals(mq.hasErrors(), actual == null);
    assertEquals(
        Joiner.on('\n').join(messages),
        Joiner.on('\n').join(mq.getMessages()));
  }

  private void assertFreeVars(String src, String... freeVars) throws Exception {
    YSON yson = YSON.parse(src);
    Joiner j = Joiner.on(", ");
    assertEquals(j.join(freeVars), j.join(yson.getFreeNames()));
  }

  private Tester withCode(String js) { return new Tester(js); }

  private static class Tester {
    private final String code;
    private final Set<String> vsd = Sets.newLinkedHashSet();
    private final Set<String> rn = Sets.newLinkedHashSet();
    private final Set<String> fn = Sets.newLinkedHashSet();

    Tester(String code) { this.code = code; }

    Tester vsd(String... names) {
      vsd.addAll(Arrays.asList(names));
      return this;
    }
    Tester rn(String... names) {
      rn.addAll(Arrays.asList(names));
      return this;
    }
    Tester fn(String... names) {
      fn.addAll(Arrays.asList(names));
      return this;
    }
    void run() throws ParseException {
      YSON yson = YSON.parse(code);
      assertEquals("vsd " + code, vsd, yson.getVarScopeDeclarations());
      assertEquals("rn " + code, rn, yson.getRequiredNames());
      assertEquals("fn " + code, fn, yson.getFreeNames());
    }
  }

  private void assertNotYson(String src, String... msgs) {
    MessageQueue mq = new MessageQueue();
    assertFalse(src, YSON.isYSON(src, YSON.DEFAULT_YSON_ALLOWED, mq));
    assertEquals(
        Joiner.on("\n").join(msgs), Joiner.on("\n").join(mq.getMessages()));
  }

  private void assertYson(String src) {
    MessageQueue mq = new MessageQueue();
    if (!YSON.isYSON(src, YSON.DEFAULT_YSON_ALLOWED, mq)) {
      fail(Joiner.on("\n").join(mq.getMessages()));
    }
  }
}
