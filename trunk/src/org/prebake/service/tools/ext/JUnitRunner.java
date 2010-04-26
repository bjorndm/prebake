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

import org.prebake.core.DidYouMean;
import org.prebake.js.JsonSink;
import org.prebake.js.YSON;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.junit.internal.JUnitSystem;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * Main class for a JUnit test runner invoked by the builtin junit tool.
 * See {@code junit.js} in the parent package.
 *
 * <h2>JSON Report Structure</h2>
 * TODO: documentation here
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class JUnitRunner {

  /**
   * @param argv
   *    [test_listener_lambda, report_output_dir, report_types, test_classes...]
   */
  public static void main(String... argv) {
    YSON.Lambda testReportFilter = !"".equals(argv[0])
        ? new YSON.Lambda(argv[0]) : null;
    Path reportOutputDir = FileSystems.getDefault().getPath(argv[1]);
    Set<String> reportTypes = Sets.newHashSet(
        argv[2].toLowerCase(Locale.ENGLISH).split(","));
    String[] testClassNames = Arrays.asList(argv).subList(0, argv.length)
        .toArray(new String[0]);
    int result = run(
        new JUnitSystem() {
          public void exit(int result) {
            throw new UnsupportedOperationException();
          }
          public PrintStream out() { return System.out; }
        },
        testReportFilter, reportOutputDir, reportTypes, testClassNames);
    System.exit(result);
  }

  public static int run(
      JUnitSystem junitSystem,
      @Nullable YSON.Lambda testReportFilter, Path reportOutputDir,
      Set<String> reportTypes, String... testClassNames) {
    JUnitCore core = new JUnitCore();
    Map<String, ?> jsonReport;
    {
      final Map<TestKey, TestState> runningTests = Collections.synchronizedMap(
          Maps.<TestKey, TestState>newLinkedHashMap());
      final List<TestState> allTests = Lists.newArrayList();
      {
        final TestState[] noTests = new TestState[0];
        final int testDumpSizeLimit = 1 << 14;
        final PrintStream testOut = new PrintStream(new OutputStream() {
          @Override
          public void write(int b) {
            TestState[] tests;
            synchronized (runningTests) {
              tests = runningTests.values().toArray(noTests);
            }
            for (TestState test : tests) {
              synchronized (test) {
                if (test.out.size() < testDumpSizeLimit) { test.out.write(b); }
              }
            }
          }

          @Override
          public void write(byte[] bytes, int pos, int len) {
            TestState[] tests;
            synchronized (runningTests) {
              tests = runningTests.values().toArray(noTests);
            }
            for (TestState test : tests) {
              synchronized (test) {
                int tlen = Math.min(len, testDumpSizeLimit - test.out.size());
                if (tlen > 0) { test.out.write(bytes, pos, tlen); }
              }
            }
          }
        });
        core.addListener(new RunListener() {
          @Override public void testFailure(Failure failure) {
            TestKey k = new TestKey(failure.getDescription());
            runningTests.get(k).recordFailure(failure);
          }
          @Override public void testStarted(Description d) {
            TestState t = new TestState(d);
            runningTests.put(t.key, t);
            allTests.add(t);
          }
          @Override public void testFinished(Description d) {
            TestKey k = new TestKey(d);
            TestState t = runningTests.remove(k);
            if (t.result == null) { t.result = TestResult.success; }
          }
          @Override public void testIgnored(Description d) {
            TestState t = new TestState(d);
            t.result = TestResult.ignored;
            allTests.add(t);
          }
          @Override public void testRunStarted(Description d) {
            System.setOut(testOut);
            System.setErr(testOut);
          }
          @Override public void testRunFinished(Result result) {
            System.setOut(
                new PrintStream(new FileOutputStream(FileDescriptor.out)));
            System.setErr(
                new PrintStream(new FileOutputStream(FileDescriptor.err)));
          }
        });
        // Run the tests.
        // We intentionally ignore the result since the summary is inferrable
        // from AllTests and can be adjusted by the filter.
        core.runMain(junitSystem, testClassNames);
      }

      assert runningTests.isEmpty();
      jsonReport = jsonReport(allTests);
    }

    jsonReport = applyReportFilter(testReportFilter, jsonReport);

    boolean generatedReports = true;
    if (reportTypes.remove("json")) {
      try {
        Writer out = new OutputStreamWriter(
            reportOutputDir.resolve("junit_tests.json").newOutputStream(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING),
            Charsets.UTF_8);
        try {
          JsonSink sink = new JsonSink(out);
          sink.writeValue(jsonReport);
        } finally {
          out.close();
        }
      } catch (IOException ex) {
        ex.printStackTrace();
        generatedReports = false;
      }
    }

    if (reportTypes.remove("html")) {
      try {
        JunitHtmlReportGenerator.generateHtmlReport(
            jsonReport, reportOutputDir);
      } catch (IOException ex) {
        ex.printStackTrace();
        generatedReports = false;
      }
    }

    if (!reportTypes.isEmpty()) {
      System.err.println(DidYouMean.toMessage(
          "Unrecognized report types " + reportTypes,
          reportTypes.iterator().next(), "json", "html"));
      generatedReports = false;
    }

    boolean allSucceeded = writeSummary(jsonReport, junitSystem.out());

    return generatedReports ? allSucceeded ? 0 : -1 : -2;
  }

  /**
   * Produce a structure like:<pre>
   * {
   *   tests: [
   *     {
   *       class_name: ...,
   *       method_name: ...,
   *       test_name: ...,
   *       annotations: [{ class_name: ..., text: ... }, ...],
   *       failure_message: ...,
   *       failure_trace: ...,
   *       out: ...,
   *       result: ...
   *     }, ...
   *   ],
   *   summary: {
   *     successes: ...,
   *     failures: ...,
   *     ignored: ...
   *   }
   * }
   * </pre>
   */
  private static ImmutableMap<String, ?> jsonReport(List<TestState> tests) {
    EnumMap<TestResult, Integer> summary = new EnumMap<TestResult, Integer>(
        TestResult.class);
    for (TestResult r : TestResult.values()) { summary.put(r, 0); }
    ImmutableList.Builder<ImmutableMap<String, ?>> testJson
        = ImmutableList.builder();
    for (TestState t : tests) {
      ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
      b.put("class_name", t.key.className);
      b.put("method_name", t.key.methodName);
      b.put("test_name", t.key.testName);
      if (!t.annotations.isEmpty()) {
        ImmutableList.Builder<Object> annotations = ImmutableList.builder();
        for (Annotation a : t.annotations) {
          annotations.add(ImmutableMap.of(
              "class_name", a.annotationType().getName(),
              "text", a.toString()));
        }
        b.put("annotations", annotations.build());
      }
      if (t.message != null) {
        b.put("failure_message", t.message);
      }
      if (t.trace != null) {
        b.put("failure_trace", t.trace);
      }
      if (t.out.size() > 0) {
        b.put("out", new String(t.out.toByteArray(), Charsets.UTF_8));
      }
      b.put("result", t.result.name());
      summary.put(t.result, summary.get(t.result) + 1);
      testJson.add(b.build());
    }
    ImmutableMap.Builder<String, Integer> summaryJson = ImmutableMap.builder();
    summaryJson.put("total", tests.size());
    for (Map.Entry<TestResult, Integer> count : summary.entrySet()) {
      summaryJson.put(count.getKey().name(), count.getValue());
    }
    return ImmutableMap.of(
        "tests", testJson.build(),
        "summary", summaryJson.build());
  }

  private static boolean writeSummary(
      Map<String, ?> jsonReport, PrintStream out) {
    Object summary = jsonReport.get("summary");
    if (!(summary instanceof Map<?, ?>)) {
      System.err.println("Bad summary");
      return false;
    }
    boolean ok = true;
    int total = 0, count = 0, nFailed = 0;
    List<String> parts = Lists.newArrayList();
    for (Map.Entry<?, ?> e : ((Map<?, ?>) summary).entrySet()) {
      Object key = e.getKey();
      Object value = e.getValue();
      if (!(value instanceof Number)) {
        System.err.println("Bad summary " + key + "=" + value);
        ok = false;
        continue;
      }
      int n = ((Number) value).intValue();
      if ("total".equals(key)) {
        total = n;
      } else {
        count += n;
        if (TestResult.failure.name().equals(key)
            || TestResult.error.name().equals(key)) {
          nFailed += n;
        }
        parts.add(key + ": " + n);
      }
    }
    Collections.sort(parts);
    parts.add("total: " + total);
    out.println(Joiner.on(", ").join(parts));
    return ok && total != 0 && nFailed == 0;
  }

  private static Map<String, ?> applyReportFilter(
      @Nullable YSON.Lambda testReportFilter, Map<String, ?> jsonReport) {
    if (testReportFilter != null) {
      // TODO: Run the test report filter over the JSON form.
      // It can do things like look for annotations, and raise or lower the
      // severity of failures, or regroup tests.
    }
    return jsonReport;
  }

  private static final class TestKey {
    final String className;
    final String methodName;
    final String testName;
    final int hashCode;

    TestKey(Description d) {
      this(d.getClassName(), d.getMethodName(), d.getDisplayName());
    }

    TestKey(
        @Nullable String className, @Nullable String methodName,
        @Nullable String testName) {
      this.className = className;
      this.methodName = methodName;
      this.testName = testName;
      this.hashCode = (className != null ? className.hashCode() : 0)
          + 31 * ((methodName != null ? methodName.hashCode() : 0)
                  + 31 * (testName != null ? testName.hashCode() : 0));
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof TestKey)) { return false; }
      TestKey that = (TestKey) o;
      return Objects.equal(this.className, that.className)
          && Objects.equal(this.methodName, that.methodName)
          && Objects.equal(this.testName, that.testName);
    }

    @Override public int hashCode() { return hashCode; }
  }

  private static final class TestState {
    final TestKey key;
    final List<Annotation> annotations;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    String message;
    String trace;
    TestResult result;

    TestState(Description d) {
      this.key = new TestKey(d);
      this.annotations = ImmutableList.copyOf(d.getAnnotations());
    }

    void recordFailure(Failure f) {
      this.message = f.getMessage();
      this.trace = f.getTrace();
      this.result = (
          f.getException() instanceof AssertionError
          || f.getException() instanceof junit.framework.AssertionFailedError)
          ? TestResult.failure
          : TestResult.error;
    }
  }

  private enum TestResult {
    success,
    error,
    failure,
    ignored,
    ;
  }
}
