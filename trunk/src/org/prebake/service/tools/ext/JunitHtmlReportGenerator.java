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

import org.prebake.js.JsonSink;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;

/**
 * This class generates a tree of HTML files based on the results of running
 * JUnit tests using {@link JUnitRunner}.
 * See the unittests for the file tree structure.
 *
 * <h2>Directory Structure</h2>
 * The HTML structure is very simple -- each file loads a script, a stylesheet,
 * has a navigation bar of links to ancestor pages, a summary of test results
 * for the current page and pages linked to.
 * <p>Tests are grouped hierarchically: into packages, then into classes,
 * then by individual test method.  Each grouping has an HTML file, so
 * index/{@code org.foo.myPackage.html} contains the summary for that package,
 * and the name without the extension is a directory, {@code org.foo,myPackage/}
 * which contains all the files named after the classes in that package.
 * <p>This convention of having a directory with the same name (minus extension)
 * as the summary HTML file is used many places below to simplify the logic,
 * especially around relative links, and is why there is a directory named
 * {@code index}.
 *
 * <h2>Integration Points -- Scripts and CSS</h2>
 * Each HTML page loads a CSS and JavaScript file, {@code junit_report.{css,js}}
 * from the same directory that contains the {@code index.html} file.
 * The CSS can hook into the HTML classes
 * (see {@link JsunitHtmlReportGeneratorTest} for examples), and the JS file
 * should define a {@code startup} method that takes as input a string of
 * arrays describing the scope of the file
 * {@code ['index', '<package-name>', '<class-name>', '<test-name>']}, and a
 * array of JSON test data similar in structure to the {@code tests} member
 * of the original JSON report.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class JunitHtmlReportGenerator {
  // TODO: move property name string literals into constants shared with
  // JUnitRunner.
  static void generateHtmlReport(
      Map<String, ?> jsonReport, Path reportDir)
      throws IOException {
    // Group tests by packages so we can let users examine the results by
    // logical groupings.
    ImmutableMultimap<String, Map<?, ?>> byPackage;
    {
      ImmutableList.Builder<Map<?, ?>> b = ImmutableList.builder();
      // We can't trust the jsonReport to have the same structure as the method
      // above, since a filter could arbitrarily change it.
      Object tests = jsonReport.get("tests");
      if (tests instanceof Iterable<?>) {
        for (Object testVal : (Iterable<?>) tests) {
          if (!(testVal instanceof Map<?, ?>)) {
            continue;  // If filter nulls out elements.
          }
          b.add((Map<?, ?>) testVal);
        }
      }
      byPackage = groupBy(b.build(), new Function<Map<?, ?>, String>() {
        public String apply(Map<?, ?> test) {
          String className = getIfOfType(test, "class_name", String.class);
          if (className == null) { return null; }
          int lastDot = className.lastIndexOf('.');
          return (lastDot >= 0) ? className.substring(0, lastDot) : "";
        }
      });
    }
    Map<String, Integer> summary = Maps.newHashMap();
    ImmutableList.Builder<Html> table = ImmutableList.builder();
    // Now, call out to create the package groupings, which in turn,
    // create the class level groupings, which in turn create pages for
    // individual tests.
    // As we descend into the test tree, each recursive call updates summary
    // info.
    Path outFile = reportDir.resolve("index.html");
    if (outFile.getParent().notExists()) {
      outFile.getParent().createDirectory();
    }
    String[] packageNames = byPackage.keySet().toArray(NO_STRINGS);
    Arrays.sort(packageNames);
    for (String packageName : packageNames) {
      Collection<Map<?, ?>> tests = byPackage.get(packageName);
      Map<String, Integer> itemSummary = generateHtmlReportOnePackage(
          packageName, tests, reportDir.resolve("index"));
      bagPutAll(itemSummary, summary);
      table.add(htmlLink("index/" + packageName + ".html", packageName))
          .add(summaryToHtml(itemSummary));
    }
    writeReport(outFile, "JUnit", table.build(), summary, jsonReport, "index");
  }

  private static Map<String, Integer> generateHtmlReportOnePackage(
      String packageName, Collection<Map<?, ?>> tests, Path reportDir)
      throws IOException {
    ImmutableMultimap<String, Map<?, ?>> byClass = groupBy(
        tests, new Function<Map<?, ?>, String>() {
          public String apply(Map<?, ?> test) {
            String className = getIfOfType(test, "class_name", String.class);
            int lastDot = className.lastIndexOf('.');
            return lastDot >= 0 ? className.substring(lastDot + 1) : className;
          }
        });
    Map<String, Integer> summary = Maps.newHashMap();
    ImmutableList.Builder<Html> table = ImmutableList.builder();
    Path outFile = reportDir.resolve(packageName + ".html");
    if (outFile.getParent().notExists()) {
      outFile.getParent().createDirectory();
    }
    String[] classNames = byClass.keySet().toArray(NO_STRINGS);
    Arrays.sort(classNames);
    for (String className : classNames) {
      Collection<Map<?, ?>> classTests = byClass.get(className);
      Map<String, Integer> itemSummary = generateHtmlReportOneClass(
          packageName, className, classTests, reportDir.resolve(packageName));
      bagPutAll(itemSummary, summary);
      table.add(htmlLink(packageName + "/" + className + ".html", className))
          .add(summaryToHtml(itemSummary));
    }
    writeReport(
        outFile, "package " + packageName, table.build(), summary, tests,
        "index", packageName);
    return summary;
  }

  private static Map<String, Integer> generateHtmlReportOneClass(
      String packageName, String className, Collection<Map<?, ?>> tests,
      Path reportDir)
      throws IOException {
    ImmutableMultimap<String, Map<?, ?>> byTestName = groupBy(
        tests, new Function<Map<?, ?>, String>() {
          int counter = 0;
          public String apply(Map<?, ?> test) {
            String methodName = getIfOfType(test, "method_name", String.class);
            if (methodName != null) { return methodName; }
            String testName = getIfOfType(test, "test_name", String.class);
            if (testName != null) { return testName; }
            return "#" + (counter++);
          }
        });
    ImmutableList.Builder<Html> table = ImmutableList.builder();
    Map<String, Integer> summary = Maps.newHashMap();
    Path outFile = reportDir.resolve(className + ".html");
    if (outFile.getParent().notExists()) {
      outFile.getParent().createDirectory();
    }
    String[] testNames = byTestName.keySet().toArray(NO_STRINGS);
    Arrays.sort(testNames);
    for (String testName : testNames) {
      int counter = 0;
      for (Map<?, ?> test : byTestName.get(testName)) {
        int testIndex = counter++;
        Map<String, Integer> itemSummary = generateHtmlReportOneTest(
            packageName, className, testName, testIndex, test,
            reportDir.resolve(className));
        bagPutAll(itemSummary, summary);
        table.add(htmlLink(
            className + "/" + testName + "_" + testIndex + ".html", testName))
            .add(summaryToHtml(itemSummary));
      }
    }
    writeReport(
        outFile, "class " + className, table.build(), summary, tests,
        "index", packageName, className);
    return summary;
  }

  private static Map<String, Integer> generateHtmlReportOneTest(
      String packageName, String className, String testName, int testIndex,
      Map<?, ?> test, Path reportDir)
      throws IOException {
    String testId = testName + "_" + testIndex;
    String result = getIfOfType(test, "result", String.class);
    if (result == null) { result = "unknown"; }
    Map<String, Integer> summary = Collections.singletonMap(result, 1);
    ImmutableList.Builder<Html> table = ImmutableList.builder();
    String displayName;
    {
      displayName = getIfOfType(test, "test_name", String.class);
      if (displayName != null && !"".equals(displayName)) {
        table.add(htmlFromString("Name")).add(htmlFromString(displayName));
      } else {
        displayName = testName;
      }
    }
    {
      String failureMsg = getIfOfType(test, "failure_message", String.class);
      if (failureMsg != null && !"".equals(failureMsg)) {
        table.add(htmlFromString("Cause")).add(htmlFromString(failureMsg));
      }
    }
    {
      String failureTrace = getIfOfType(test, "failure_trace", String.class);
      if (failureTrace != null && !"".equals(failureTrace)) {
        table.add(htmlFromString("Trace")).add(htmlFromString(failureTrace));
      }
    }
    {
      String output = getIfOfType(test, "out", String.class);
      if (output != null && !"".equals(output)) {
        table.add(htmlFromString("Output")).add(htmlFromString(output));
      }
    }
    Path outFile = reportDir.resolve(testId + ".html");
    if (outFile.getParent().notExists()) {
      outFile.getParent().createDirectory();
    }
    writeReport(
        outFile, "test " + displayName, table.build(), summary,
        ImmutableList.of(test),  // wrap in a list for consistency
        "index", packageName, className, testId);
    return summary;
  }

  private static String nParent(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = n; --i >= 0;) { sb.append("../"); }
    return sb.toString();
  }

  private static void writeReport(
      Path outFile, String title, List<Html> table,
      Map<String, Integer> summary, Object json, String... navBar)
      throws IOException {
    int depth = navBar.length;
    String baseDir = nParent(depth - 1);
    Writer out = new OutputStreamWriter(outFile.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
        Charsets.UTF_8);
    try {
      // First, write out some style and script links so that clients can
      // customize the content.
      out.append("<html><head><title>");
      appendHtml(out, title);
      out.append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"")
          .append(baseDir)
          .append("junit_report.css\" /><script src=\"")
          .append(baseDir)
          .append("junit_report.js\"></script>");
      if (json != null) {
        out.append("<script type=\"text/javascript\">startup(")
            .append(JsonSink.stringify(Arrays.asList(navBar))
                    .replaceAll("</", "<\\/"))
            .append(", ")
            .append(JsonSink.stringify(json))
            .append(");</script>");
      }
      out.append("</head><body class=\"tree_level_")
          .append(Integer.toString(depth))
          .append("\">");
      { // The title/navigation bar
        out.append("<h1>");
        for (int i = 0; i < depth - 1; ++i) {
          out.append("<a class=\"nav_anc\" href=\"")
              .append(nParent(depth - i - 1))
              .append(navBar[i])
              .append(".html\">");
          appendHtml(out, navBar[i]);
          out.append("</a><span class=\"nav_sep\">|</span>");
        }
        out.append("<span class=\"nav_top\">");
        appendHtml(out, navBar[depth - 1]);
        out.append("</span></h1>");
      }
      out.append("<span class=\"page_summary\">");  // The summary
      summaryToHtml(summary).appendTo(out);
      out.append("</span>");

      out.append("<table class=\"data_table\">");
      for (int i = 0, n = table.size(); i < n; i += 2) {
        Html name = table.get(i), value = table.get(i + 1);
        out.append("<tr class=\"data_row ");
        appendHtml(out, name.asPlainText());
        out.append("\"><td class=\"key\">");
        name.appendTo(out);
        out.append("</td><td class=\"value\">");
        value.appendTo(out);
        out.append("</tr>");
      }
      out.append("</table></body></html>");
    } finally {
      out.close();
    }
  }

  private static Html summaryToHtml(Map<String, Integer> summary) {
    String[] summaryKeys = summary.keySet().toArray(NO_STRINGS);
    Arrays.sort(summaryKeys);
    int total = 0;
    ImmutableList.Builder<Html> parts = ImmutableList.builder();
    Html sep = htmlSpanTag("summary_sep", ",");
    for (String summaryKey : summaryKeys) {
      int n = summary.get(summaryKey);
      parts.add(summaryPairToHtml(summaryKey, n)).add(sep);
      total += n;
    }
    parts.add(summaryPairToHtml("total", total));
    return htmlConcat(parts.build());
  }

  private static Html summaryPairToHtml(final String summaryKey, final int n) {
    return new Html() {
      public void appendTo(Appendable out) throws IOException {
        out.append("<span class=\"summary_pair ");
        appendHtml(out, summaryKey);
        out.append("\"><span class=\"summary_key\">");
        appendHtml(out, summaryKey);
        out.append("</span><span class=\"summary_spacer\">:</span>")
            .append("<span class=\"summary_value\">")
            .append(Integer.toString(n))
            .append("</span></span>");
      }

      public String asPlainText() {
        return summaryKey + ":" + n;
      }
    };
  }


  static void appendHtml(Appendable out, String plainText)
      throws IOException {
    // TODO: replace this with a library function
    int pos = 0;
    int n = plainText.length();
    for (int i = 0; i < n; ++i) {
      char ch = plainText.charAt(i);
      switch (ch) {
        case '<': case '>': case '&': case '"': break;
        default:
          if (ch >= 0xa && ch <= 0x7f) { continue; }
      }
      out.append(plainText, pos, i)
          .append("&#")
          .append(Integer.toString(ch)) // TODO: handle supplementary codepoints
          .append(";");
      pos = i + 1;
    }
    out.append(plainText, pos, n);
  }

  private static <T> void bagPutAll(
      Map<T, Integer> sourceBag, Map<T, Integer> destBag) {
    for (Map.Entry<T, Integer> summaryEntry : sourceBag.entrySet()) {
      T k = summaryEntry.getKey();
      int delta = summaryEntry.getValue();
      Integer oldCount = destBag.get(k);
      int newCount = delta + (oldCount != null ? oldCount : 0);
      if (newCount != 0) {
        destBag.put(k, newCount);
      } else {
        destBag.remove(k);
      }
    }
  }

  private static ImmutableMultimap<String, Map<?, ?>> groupBy(
      Iterable<Map<?, ?>> tests, Function<Map<?, ?>, String> keyFn) {
    ImmutableMultimap.Builder<String, Map<?, ?>> b
        = ImmutableMultimap.builder();
    for (Map<?, ?> test : tests) {
      String name = keyFn.apply(test);
      if (name != null) {
        b.put(name, test);
      }
    }
    return b.build();
  }

  private static @Nullable <T>
  T getIfOfType(Map<?, ?> map, Object key, Class<T> type) {
    Object value = map.get(key);
    if (value != null && type.isInstance(value)) {
      return type.cast(value);
    }
    return null;
  }

  private static interface Html {
    void appendTo(Appendable out) throws IOException;
    String asPlainText();
  }

  static Html htmlFromString(final String plainText) {
    return new Html() {
      public void appendTo(Appendable out) throws IOException {
        appendHtml(out, plainText);
      }
      public String asPlainText() { return plainText; }
    };
  }

  static Html htmlLink(String href, String body) {
    return htmlLink(href, htmlFromString("".equals(body) ? "\u00A0" : body));
  }

  static Html htmlLink(final String href, final Html body) {
    return new Html() {
      public void appendTo(Appendable out) throws IOException {
        out.append("<a href=\"");
        appendHtml(out, href);
        out.append("\">");
        body.appendTo(out);
        out.append("</a>");
      }
      public String asPlainText() { return body.asPlainText(); }
    };
  }

  static Html htmlSpanTag(String classes, String body) {
    return htmlSpanTag(
        classes, htmlFromString("".equals(body) ? "\u00A0" : body));
  }

  static Html htmlSpanTag(final String classes, final Html body) {
    return new Html() {
      public void appendTo(Appendable out) throws IOException {
        out.append("<span class=\"");
        appendHtml(out, classes);
        out.append("\">");
        body.appendTo(out);
        out.append("</span>");
      }
      public String asPlainText() { return body.asPlainText(); }
    };
  }

  static Html htmlConcat(Iterable<Html> html) {
    final ImmutableList<Html> parts = ImmutableList.copyOf(html);
    return new Html() {
      public void appendTo(Appendable out) throws IOException {
        for (Html html : parts) { html.appendTo(out); }
      }
      public String asPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Html html : parts) { sb.append(html.asPlainText()); }
        return sb.toString();
      }
    };
  }

  private static final String[] NO_STRINGS = new String[0];
}
