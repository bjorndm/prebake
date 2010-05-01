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

package org.prebake.service.tools.ext;

import org.prebake.js.JsonSource;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import org.junit.Test;

public class JunitHtmlReportGeneratorTest extends PbTestCase {
  @Test public final void testReportGeneration() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  reports/",
            "    tests/"));
    JsonSource src = new JsonSource(new StringReader(Joiner.on('\n').join(
        "{",
        "  \"tests\": [",
        "    {",
        "      \"class_name\": \"foo.Bar\",",
        "      \"method_name\": \"iPass\",",
        "      \"test_name\": \"iPass\",",
        "      \"out\": \"Hello, World!\",",
        "      \"result\": \"success\"",
        "    },",
        "    null,",
        "    {",
        "      \"class_name\": \"foo.Bar\",",
        "      \"method_name\": \"iFail\",",
        "      \"test_name\": \"I_failed\",",
        "      \"failure_message\": \"AssertionError: true is not false\",",
        "      \"failure_trace\": \"  at ifail()\",",
        "      \"result\": \"failure\"",
        "    },",
        "    {",
        "      \"class_name\": \"foo.Bar\",",
        "      \"method_name\": \"iErroredOut\",",
        "      \"test_name\": \"I Errored Out\",",
        "      \"out\": \"Something fishy is going on!\",",
        "      \"failure_message\": \"Boom\",",
        "      \"failure_trace\": \"\tat boom\\n\tat tick\\n\tat tick\",",
        "      \"result\": \"error\"",
        "    },",
        "    {",
        "      \"class_name\": \"foo.Baz\",",
        "      \"method_name\": \"boo\",",
        "      \"test_name\": \"boo\",",
        "      \"annotations\": [{ \"class_name\": \"org.junit.Test\" }],",
        "      \"result\": \"success\"",
        "    },",
        "    {",
        "      \"class_name\": \"boo.Baz\",",
        "      \"method_name\": \"test1\",",
        "      \"test_name\": \"test1\",",
        "      \"out\": \"One\",",
        "      \"result\": \"success\"",
        "    },",
        "    {",
        "      \"class_name\": \"foo.Baz\",",
        "      \"method_name\": \"far\",",
        "      \"test_name\": \"far\",",
        "      \"result\": \"failure\"",
        "    },",
        "    {",
        "      \"class_name\": \"boo.Baz\",",
        "      \"method_name\": \"test2\",",
        "      \"test_name\": \"test2\",",
        "      \"failure_message\": \"broken\",",
        "      \"failure_trace\": \"  at test2\",",
        "      \"result\": \"failure\"",
        "    },",
        "    {",
        "      \"class_name\": \"org.example.AllTests\",",
        "      \"method_name\": \"test\",",
        "      \"test_name\": \"test\",",
        "      \"result\": \"success\"",
        "    },",
        "    {",
        "      \"class_name\": \"foo.bar.baz.Boo\",",
        "      \"method_name\": \"testing\",",
        "      \"test_name\": \"testing\",",
        "      \"out\": \"1 < 2 && 4 > 3\",",
        "      \"result\": \"success\"",
        "    },",
        "    {",
        "      \"class_name\": \"poe.allen.Edgar\",",
        "      \"method_name\": \"the_raven\",",
        "      \"test_name\": \"the_raven\",",
        "      \"out\": \"Quoth the raven, \\\"Nevermore!\\\"\",",
        "      \"result\": \"classic\"",
        "    }",
        "  ],",
        "  \"summary\": {",
        "    \"failures\": 3,",
        "    \"errors\": 1,",
        "    \"classic\": 1,",
        "    \"success\": 5,",
        "    \"total\": 10",
        "  }",
        "}")));
    Map<String, ?> json = src.nextObject();
    JunitHtmlReportGenerator.generateHtmlReport(
        json, fs.getPath("/reports/tests"));
    assertEquals(
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "  reports/",
            "    tests/",
            "      index/",
            "        boo/",
            "          Baz/",
            "            test1_0.html \"...\"",
            "            test2_0.html \"...\"",
            "          Baz.html \"...\"",
            "        boo.html \"...\"",
            "        foo/",
            "          Bar/",
            "            iErroredOut_0.html \"...\"",
            "            iFail_0.html \"...\"",
            "            iPass_0.html \"...\"",
            "          Bar.html \"...\"",
            "          Baz/",
            "            boo_0.html \"...\"",
            "            far_0.html \"...\"",
            "          Baz.html \"...\"",
            "        foo.html \"...\"",
            "        foo.bar.baz/",
            "          Boo/",
            "            testing_0.html \"...\"",
            "          Boo.html \"...\"",
            "        foo.bar.baz.html \"...\"",
            "        org.example/",
            "          AllTests/",
            "            test_0.html \"...\"",
            "          AllTests.html \"...\"",
            "        org.example.html \"...\"",
            "        poe.allen/",
            "          Edgar/",
            "            the_raven_0.html \"...\"",
            "          Edgar.html \"...\"",
            "        poe.allen.html \"...\"",
            "      index.html \"...\"",
            "      junit_report.css \"...\"",
            ""),
        fileSystemToAsciiArt(fs, 40));
    // Make sure that all the links are live.
    final List<String> badLinks = Lists.newArrayList();
    final List<String> goodLinks = Lists.newArrayList();
    Files.walkFileTree(
        fs.getPath("/reports/tests"), new SimpleFileVisitor<Path>() {
          final Pattern hrefPattern = Pattern.compile(
              "<a\\b(?:[^>]*?)\\bhref=\"([^\"]*)\"",
              Pattern.CASE_INSENSITIVE);
          @Override

          public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
            if (p.getName().toString().endsWith(".html")) {
              try {
                Reader in = new InputStreamReader(
                    p.newInputStream(), Charsets.UTF_8);
                try {
                  String content = CharStreams.toString(in);
                  Matcher m = hrefPattern.matcher(content);
                  while (m.find()) {
                    String href = m.group(1);
                    Path linkTarget = p.getParent().resolve(href).normalize();
                    if (!linkTarget.exists()) {
                      badLinks.add(linkTarget.toString());
                      System.err.println("Found " + href + " in " + p);
                      System.err.println(linkTarget + " does not exist");
                    } else {
                      goodLinks.add(linkTarget.toString());
                    }
                  }
                } finally {
                  in.close();
                }
              } catch (IOException ex) {
                Throwables.propagate(ex);
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
    assertTrue(badLinks.toString(), badLinks.isEmpty());
    System.err.println("Found " + goodLinks.size() + " links");
    assertTrue(goodLinks.size() > 60);
    assertEquals(
        Joiner.on("").join(
            "<html><head><title>class Boo</title>",
            // Boilerplate customization hooks.
            "<link rel=\"stylesheet\" type=\"text/css\"",
            " href=\"../../junit_report.css\" />",
            "<script src=\"../../junit_report.js\"></script>",
            // The object digest.  A list of one test for this class.
            "<script type=\"text/javascript\">",
            "startup([\"index\",\"foo.bar.baz\",\"Boo\"],",
            " [{\"class_name\":\"foo.bar.baz.Boo\",",
            "\"method_name\":\"testing\",",
            "\"test_name\":\"testing\",",
            "\"out\":\"1 < 2 && 4 > 3\",",
            "\"result\":\"success\"}]);",
            "</script>",
            "</head>",
            "<body class=\"tree_level_3\">",
            // The header
            "<h1>",
            "<a class=\"nav_anc\" href=\"../../index.html\">index</a>",
            "<span class=\"nav_sep\">|</span>",
            "<a class=\"nav_anc\" href=\"../foo.bar.baz.html\">foo.bar.baz</a>",
            "<span class=\"nav_sep\">|</span>",
            "<span class=\"nav_top\">Boo</span>",
            "</h1>",
            // Summary
            "<span class=\"page_summary\">",
            summaryHtml("classic", 0, "error", 0, "failure", 0, "success", 1),
            "</span>",
            // The list of tests with links
            "<table class=\"data_table\">",
            "<tr class=\"data_row even testing\">",
            "<td class=\"key\"><a href=\"Boo/testing_0.html\">testing</a></td>",
            "<td class=\"value\">",
            "<span class=\"summary\">",
            summaryHtml("classic", 0, "error", 0, "failure", 0, "success", 1),
            "</span></td>",
            "<td class=\"preview\"></td>",
            "</table>",
            "</body></html>").replace(">", ">\n"),
        CharStreams.toString(
            new InputStreamReader(fs.getPath(
                "/reports/tests/index/foo.bar.baz/Boo.html").newInputStream(),
            Charsets.UTF_8)).replace(">", ">\n"));
    assertEquals(
        Joiner.on("").join(
            "<html><head><title>test I Errored Out</title>",
            // Boilerplate customization hooks.
            "<link rel=\"stylesheet\" type=\"text/css\"",
            " href=\"../../../junit_report.css\" />",
            "<script src=\"../../../junit_report.js\"></script>",
            // The object digest.  Just the one test.
            "<script type=\"text/javascript\">",
            "startup([\"index\",\"foo\",\"Bar\",\"iErroredOut_0\"],",
            " [{\"class_name\":\"foo.Bar\",",
            "\"method_name\":\"iErroredOut\",",
            "\"test_name\":\"I Errored Out\",",
            "\"out\":\"Something fishy is going on!\",",
            "\"failure_message\":\"Boom\",",
            "\"failure_trace\":\"\\tat boom\\n\\tat tick\\n\\tat tick\",",
            "\"result\":\"error\"}]);",
            "</script>",
            "</head>",
            "<body class=\"tree_level_4\">",
            // The header
            "<h1>",
            "<a class=\"nav_anc\" href=\"../../../index.html\">index</a>",
            "<span class=\"nav_sep\">|</span>",
            "<a class=\"nav_anc\" href=\"../../foo.html\">foo</a>",
            "<span class=\"nav_sep\">|</span>",
            "<a class=\"nav_anc\" href=\"../Bar.html\">Bar</a>",
            "<span class=\"nav_sep\">|</span>",
            "<span class=\"nav_top\">iErroredOut_0</span>",
            "</h1>",
            // Summary
            "<span class=\"page_summary\">",
            summaryHtml("classic", 0, "error", 1, "failure", 0, "success", 0),
            "</span>",
            // The test details.
            "<table class=\"data_table\">",
            "<tr class=\"data_row even Name\">",
            "<td class=\"key\">Name</td>",
            "<td class=\"value\">I Errored Out</td>",
            "<tr class=\"data_row odd Cause\">",
            "<td class=\"key\">Cause</td>",
            "<td class=\"value\">Boom</td>",
            "<tr class=\"data_row even Trace\">",
            "<td class=\"key\">Trace</td>",
            "<td class=\"value\">",
            "<span class=\"unfiltered\">",
            "&#9;at boom\n&#9;at tick\n&#9;at tick",
            "</span>\n",
            "</td>",
            "<tr class=\"data_row odd Output\">",
            "<td class=\"key\">Output</td>",
            "<td class=\"value\">Something fishy is going on!</td>",
            "</table>",
            "</body></html>").replace(">", ">\n"),
        CharStreams.toString(
            new InputStreamReader(fs.getPath(
                "/reports/tests/index/foo/Bar/iErroredOut_0.html")
                .newInputStream(),
            Charsets.UTF_8)).replace(">", ">\n"));
    assertEquals(
        Joiner.on("").join(
            "<html><head><title>package foo</title>",
            // Boilerplate customization hooks.
            "<link rel=\"stylesheet\" type=\"text/css\"",
            " href=\"../junit_report.css\" />",
            "<script src=\"../junit_report.js\"></script>",
            // The object digest.  A list of tests for this package.
            "<script type=\"text/javascript\">",
            "startup([\"index\",\"foo\"], [{\"class_name\":\"foo.Bar\",",
            "\"method_name\":\"iPass\",",
            "\"test_name\":\"iPass\",",
            "\"out\":\"Hello, World!\",",
            "\"result\":\"success\"},",
            "{\"class_name\":\"foo.Bar\",",
            "\"method_name\":\"iFail\",",
            "\"test_name\":\"I_failed\",",
            "\"failure_message\":\"AssertionError: true is not false\",",
            "\"failure_trace\":\"  at ifail()\",",
            "\"result\":\"failure\"},",
            "{\"class_name\":\"foo.Bar\",",
            "\"method_name\":\"iErroredOut\",",
            "\"test_name\":\"I Errored Out\",",
            "\"out\":\"Something fishy is going on!\",",
            "\"failure_message\":\"Boom\",",
            "\"failure_trace\":\"\\tat boom\\n\\tat tick\\n\\tat tick\",",
            "\"result\":\"error\"},",
            "{\"class_name\":\"foo.Baz\",",
            "\"method_name\":\"boo\",",
            "\"test_name\":\"boo\",",
            "\"annotations\":[{\"class_name\":\"org.junit.Test\"}],",
            "\"result\":\"success\"},",
            "{\"class_name\":\"foo.Baz\",",
            "\"method_name\":\"far\",",
            "\"test_name\":\"far\",",
            "\"result\":\"failure\"}]);",
            "</script>",
            "</head>",
            "<body class=\"tree_level_2\">",
            // The header
            "<h1>",
            "<a class=\"nav_anc\" href=\"../index.html\">index</a>",
            "<span class=\"nav_sep\">|</span>",
            "<span class=\"nav_top\">foo</span>",
            "</h1>",
            // Summary
            "<span class=\"page_summary\">",
            summaryHtml("classic", 0, "error", 1, "failure", 2, "success", 2),
            "</span>",
            // The list of classes with links
            "<table class=\"data_table\">",
            "<tr class=\"data_row even Bar\">",
            "<td class=\"key\"><a href=\"foo/Bar.html\">Bar</a></td>",
            "<td class=\"value\">",
            "<span class=\"summary\">",
            summaryHtml("classic", 0, "error", 1, "failure", 1, "success", 1),
            "</span>",
            "</td>",
            "<tr class=\"data_row odd Baz\">",
            "<td class=\"key\"><a href=\"foo/Baz.html\">Baz</a></td>",
            "<td class=\"value\">",
            "<span class=\"summary\">",
            summaryHtml("classic", 0, "error", 0, "failure", 1, "success", 1),
            "</span>",
            "</td>",
            "</table>",
            "</body></html>").replace(">", ">\n"),
        CharStreams.toString(
            new InputStreamReader(fs.getPath(
                "/reports/tests/index/foo.html").newInputStream(),
            Charsets.UTF_8)).replace(">", ">\n"));
  }

  @Test public final void testTraceFiltering() throws IOException {
    String trace = Joiner.on('\n').join(
        ("org.junit.ComparisonFailure: expected:"
         + "<{\"name\":\"[cp\",\"help\":{\"summary\":"
         + "\"Copies files to a directory tree.\",\"detail\":"
         + "\"This version of the cp command copies by glob transform.\\n"
         + "E.g. to copy all html files under the doc/ directory to \\n"
         + "the same location under the www directory do"
         + "\\n  tools.cp(\\\"doc/**.html\\\", \\\"www/**.html\\\");\","
         + "\"contact\":\"Mike Samuel <mikesamuel@gmail.com>\"}}"
         + " ; {\"name\":\"]bar\",\"help\":\"an exam...>"
         + " but was:<{\"name\":\"[]bar\",\"help\":\"an exam...>"),
        "\tat org.junit.Assert.assertEquals(Assert.java:123)",
        "\tat org.junit.Assert.assertEquals(Assert.java:145)",
        ("\tat org.prebake.service.tools.ToolBoxTest$TestRunner.assertSigs"
         + "(ToolBoxTest.java:221)"),
        ("\tat org.prebake.service.tools.ToolBoxTest.testBuiltin"
         + "(ToolBoxTest.java:112)"),
        "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
        "\tat java.lang.reflect.Method.invoke(Method.java:613)",
        ("\tat org.junit.runners.BlockJUnit4ClassRunner.runChild"
         + "(BlockJUnit4ClassRunner.java:50)"),
        "\tat org.junit.runners.ParentRunner$3.run(ParentRunner.java:193)",
        "\tat org.junit.runners.Suite.runChild(Suite.java:24)",
        "\tat org.junit.runner.JUnitCore.run(JUnitCore.java:117)",
        "\tat org.junit.runner.JUnitCore.runMain(JUnitCore.java:98)",
        ("\tat org.prebake.service.tools.ext.JUnitRunner.run"
         + "(JUnitRunner.java:176)"),
        ("\tat org.prebake.service.tools.ext.JUnitRunner.main"
         + "(JUnitRunner.java:90)")
        );
    JunitHtmlReportGenerator.Html traceHtml
        = JunitHtmlReportGenerator.htmlFromTrace(trace);
    assertEquals(trace + "\n", traceHtml.asPlainText());
    StringBuilder html = new StringBuilder();
    traceHtml.appendTo(html);
    assertEquals(
        Joiner.on('\n').join(
            ("<span class=\"throwable\">"
             + "org.junit.ComparisonFailure: expected:&#60;"
             + "<span class=\"golden\">{&#34;name&#34;:&#34;"
             + "[cp&#34;,&#34;help&#34;:{&#34;summary&#34;:&#34;"
             + "Copies files to a directory tree.&#34;,&#34;detail&#34;:&#34;"
             + "This version of the cp command copies by glob transform.\\n"
             + "E.g. to copy all html files under the doc/ directory to \\n"
             + "the same location under the www directory do"
             + "\\n  tools.cp(\\&#34;doc/**.html\\&#34;,"
             + " \\&#34;www/**.html\\&#34;);&#34;,"
             + "&#34;contact&#34;:&#34;Mike Samuel"
             + " &#60;mikesamuel@gmail.com&#62;&#34;}} ; "
             + "{&#34;name&#34;:&#34;]bar&#34;,&#34;help&#34;:&#34;an exam..."
             + "</span>&#62; but was:&#60;<span class=\"actual\">"
             + "{&#34;name&#34;:&#34;[]bar&#34;,"
             + "&#34;help&#34;:&#34;an exam...</span>&#62;"
             + "</span>"),
            ("<span class=\"filtered\">"
             + "&#9;at org.junit.Assert.assertEquals(Assert.java:123)"),
            "&#9;at org.junit.Assert.assertEquals(Assert.java:145)</span>",
            ("<span class=\"unfiltered\">"
             + "&#9;at org.prebake.service.tools.ToolBoxTest$TestRunner"
             + ".assertSigs(ToolBoxTest.java:221)"),
            ("&#9;at org.prebake.service.tools.ToolBoxTest.testBuiltin"
             + "(ToolBoxTest.java:112)</span>"),
            ("<span class=\"filtered\">"
             + "&#9;at sun.reflect.NativeMethodAccessorImpl.invoke0"
             + "(Native Method)"),
            "&#9;at java.lang.reflect.Method.invoke(Method.java:613)",
            ("&#9;at org.junit.runners.BlockJUnit4ClassRunner.runChild"
             + "(BlockJUnit4ClassRunner.java:50)"),
            ("&#9;at org.junit.runners.ParentRunner$3.run"
             + "(ParentRunner.java:193)"),
            "&#9;at org.junit.runners.Suite.runChild(Suite.java:24)",
            "&#9;at org.junit.runner.JUnitCore.run(JUnitCore.java:117)",
            "&#9;at org.junit.runner.JUnitCore.runMain(JUnitCore.java:98)",
            ("&#9;at org.prebake.service.tools.ext.JUnitRunner.run"
             + "(JUnitRunner.java:176)"),
            ("&#9;at org.prebake.service.tools.ext.JUnitRunner.main"
             + "(JUnitRunner.java:90)</span>"),
            ""),
        html.toString());
  }

  @Test public final void testSummaryHtml() {
    assertEquals(
        Joiner.on("").join(
            "<span class=\"summary_pair total\">",
            "<span class=\"summary_key\">total</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">0</span>",
            "</span>"),
        summaryHtml());
    assertEquals(
        Joiner.on("").join(
            "<span class=\"summary_pair success\">",
            "<span class=\"summary_key\">success</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">0</span>",
            "</span>",
            "<span class=\"summary_sep\">,</span>",
            "<span class=\"summary_pair total\">",
            "<span class=\"summary_key\">total</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">0</span>",
            "</span>"),
        summaryHtml("success", 0));
    assertEquals(
        Joiner.on("").join(
            "<span class=\"summary_pair nonzero error\">",
            "<span class=\"summary_key\">error</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">1</span>",
            "</span>",
            "<span class=\"summary_sep nonzero\">,</span>",
            "<span class=\"summary_pair nonzero failure\">",
            "<span class=\"summary_key\">failure</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">2</span>",
            "</span>",
            "<span class=\"summary_sep\">,</span>",
            "<span class=\"summary_pair ignored\">",
            "<span class=\"summary_key\">ignored</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">0</span>",
            "</span>",
            "<span class=\"summary_sep nonzero\">,</span>",
            "<span class=\"summary_pair nonzero success\">",
            "<span class=\"summary_key\">success</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">2</span>",
            "</span>",
            "<span class=\"summary_sep nonzero\">,</span>",
            "<span class=\"summary_pair nonzero total\">",
            "<span class=\"summary_key\">total</span>",
            "<span class=\"summary_spacer\">:</span>",
            "<span class=\"summary_value\">5</span>",
            "</span>"),
            summaryHtml("error", 1, "failure", 2, "ignored", 0, "success", 2));
  }

  private static String summaryHtml(Object... parts) {
    ImmutableMap.Builder<String, Integer> summary = ImmutableMap.builder();
    ImmutableList.Builder<String> types = ImmutableList.builder();
    for (int i = 0, n = parts.length; i < n; i += 2) {
      types.add((String) parts[i]);
      summary.put((String) parts[i], (Integer) parts[i + 1]);
    }
    StringBuilder sb = new StringBuilder();
    try {
      JunitHtmlReportGenerator.summaryToHtml(summary.build(), types.build())
          .appendTo(sb);
    } catch (IOException ex) {
      Throwables.propagate(ex);
    }
    return sb.toString();
  }
}
