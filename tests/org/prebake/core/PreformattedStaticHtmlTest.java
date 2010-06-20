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

package org.prebake.core;

import org.prebake.util.PbTestCase;

import com.google.common.base.Joiner;
import org.junit.Test;

public class PreformattedStaticHtmlTest extends PbTestCase {
  @Test public final void testHtml() {
    assertEquals(
        "Hello, World!", PreformattedStaticHtml.of("Hello, World!").html());
    assertEquals(
        "<b>Hello, World!</b>",
        PreformattedStaticHtml.of("<b>Hello, World!</b>").html());
    assertEquals(
        "<b>Hello, World!</b>",
        PreformattedStaticHtml.of("<b>Hello, World!").html());
    assertEquals(
        "<b>Hello, World!</b>",
        PreformattedStaticHtml.of(
            "<b>Hello, World!<script>alert(1)</script>").html());
    assertEquals(
        "<b>Hello, World!</b>",
        PreformattedStaticHtml.of(
            "<b onclick=\"alert(1)\">Hello, World!").html());
    assertEquals(
        "<b>Hello, World!</b>",
        PreformattedStaticHtml.of(
            "<b style=\"color: expression('alert(1)')\">Hello, World!").html());
    assertEquals(
        "<b style=\"color: red\">Hello, World!</b>",
        PreformattedStaticHtml.of("<b style=color:red>Hello, World!").html());
    assertEquals(
        ""
        + "<b style=\"color: red; font-family: &#39;Courier&#39;\""
        + ">Hello, World!</b>",
        PreformattedStaticHtml.of(
            ""
            + "<b style="
            + "color:red;background:expression('alert(1)');font-family:Courier"
            + ">Hello, World!</b>").html());
    assertEquals(
        "<ul><li>One</li><li>Two</li><li>Three</li></ul>",
        PreformattedStaticHtml.of(
            "<ul><li>One<li>Two<li>Three</ul>").html());
    assertEquals(
        ""
        + "<table><tbody><tr><th>Numerals</th><th>Letters</th></tr>"
        + "<tr><td>15</td><td>fifteen</td></tr>"
        + "<tr><td>100</td><td>one hundred</td></tr></tbody></table>",
        PreformattedStaticHtml.of(
            ""
            + "<table><tr><th>Numerals</th><th>Letters</th></tr>"
            + "<tr><td>15</td><td>fifteen</td></tr>"
            + "<tr><td>100</td><td>one hundred</td></tr></table>").html());
  }

  @Test public final void testPlainText() {
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of("Hello, World!").plainText());
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of("<b>Hello, World!</b>").plainText());
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of("<b>Hello, World!").plainText());
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of(
            "<b>Hello, World!<script>alert(1)</script>").plainText());
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of(
            "<b onclick=\"alert(1)\">Hello, World!").plainText());
    assertEquals(
        "Hello, World!",
        PreformattedStaticHtml.of(
            "<b style=\"color: red\">Hello, World!").plainText());
  }

  @Test public final void testPlainTextLists() {
    assertEquals(
        "  # One\n  # Two\n  # Three",
        PreformattedStaticHtml.of(
            "<ul><li>One<li>Two<li>Three</ul>").plainText());
    assertEquals(
        "  1 One\n  2 Two\n  3 Three",
        PreformattedStaticHtml.of(
            "<ol><li>One<li>Two<li>Three</ol>").plainText());
    assertEquals(
        Joiner.on('\n').join(
            "   1 One",
            "   2 Two",
            "   4 Four",
            "   5 Five",
            "  10 Ten"),
        PreformattedStaticHtml.of(
            "<ol><li>One<li>Two<li><li>Four<li>Five<li><li><li><li><li>Ten</ol>"
            ).plainText());
    assertEquals(
        "  a One\n  b Two\n  c Three",
        PreformattedStaticHtml.of(
            "<ol type=a><li>One<li>Two<li>Three</ol>").plainText());
    assertEquals(
        Joiner.on('\n').join(
            "    i One",
            "   ii     I Two",
            "         II Dos",
            "  iii Three",
            "   iv Four"),
        PreformattedStaticHtml.of(
            ""
            + "<ol type=i>"
            + "<li>One"
            + "<li><ol type=I><li>Two<li>Dos</ol>"
            + "<li>Three"
            + "<li>Four</ol>"
            ).plainText());
  }

  @Test public final void testPlainTextTables() {
    assertEquals(
        ""
        + "Numerals   Letters\n"
        + "15       fifteen\n"
        + "100      one hundred",
        PreformattedStaticHtml.of(
            ""
            + "<table><tr><th>Numerals</th><th>Letters</th></tr>"
            + "<tr><td>15</td><td>fifteen</td></tr>"
            + "<tr><td>100</td><td>one hundred</td></tr></table>").plainText());
    assertEquals(
        ""
        + "Numerals   Letters\n"
        + "15       fif-\n"
        + "N/A      teen\n"
        + "100      one hundred",
        PreformattedStaticHtml.of(
            ""
            + "<table><tr><th>Numerals</th><th>Letters</th></tr>"
            + "<tr><td>15</td><td rowspan=2>fif-\nteen</td></tr>"
            + "<tr><td>N/A</tr>"
            + "<tr><td>100</td><td>one hundred</td></tr></table>").plainText());
  }

  @Test public final void testPlainTextBlockElements() {
    assertEquals(
        ""
        + "                Header\n"
        + "Sub Header\n"
        + "A sentence of descriptive text with a \n"
        + "second line",
        PreformattedStaticHtml.of(
            ""
            + "<h1><center>Header</center></h1>"
            + "<h2>Sub Header</h2>"
            + "<p>A sentence of descriptive text with a <br>second line</p>")
            .plainText());

  }

  @Test public final void testPlainTextImage() {
    assertEquals(
        "",
        PreformattedStaticHtml.of("<img>").plainText());
    assertEquals(
        "[Hello, World!]",
        PreformattedStaticHtml.of("<img alt='Hello, World!'>").plainText());
  }

  @Test public final void testXmpElement() {
    assertEquals(
        "<p>HTML in Documentation</p>",
        PreformattedStaticHtml.of("<xmp><p>HTML in Documentation</p></xmp>")
            .plainText());
  }

  @Test public final void testPlaintextElement() {
    assertEquals(
        ""
        + "Header\n"
        + "<script>alert('Code in documentation');</script>",
        PreformattedStaticHtml.of(
            "<h1>Header</h1>"
            + "<plaintext><script>alert('Code in documentation');</script>")
            .plainText());
  }

  @Test public final void testNonPosixNewlines() {
    assertEquals(
        Joiner.on('\n').join(
            "Foo",
            "Bar",
            "Baz",
            "Boo",
            "Far",
            "Faz",
            ""),
        PreformattedStaticHtml.of(
            "<pre>Foo\nBar\r\nBaz\u2028Boo\u2029Far\u0085Faz\r</pre>")
            .plainText());

  }
}
