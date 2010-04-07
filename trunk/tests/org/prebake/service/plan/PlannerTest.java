package org.prebake.service.plan;

import org.prebake.core.Documentation;
import org.prebake.fs.ArtifactValidityTracker;
import org.prebake.fs.StubArtifactValidityTracker;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubScheduledExecutorService;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ValueFuture;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

public class PlannerTest extends PbTestCase {

  public final void testToolsAvailable() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
             + "  myProduct: {\\n"
             + "    inputs: [ '**.foo' ],\\n"
             + "    outputs: [ '**.bar' ],\\n"
             + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
             + "  }\\n"
             + "})\"")));
    ArtifactValidityTracker files = new StubArtifactValidityTracker(
        fs.getPath("/cwd"));
    ToolProvider toolbox = new ToolProvider() {
      public List<Future<ToolSignature>> getAvailableToolSignatures() {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(new ToolSignature("myTool", null, null, true));
        return Collections.<Future<ToolSignature>>singletonList(f);
      }
      public void close() {}
    };
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Planner p = new Planner(
        files, toolbox, Collections.singleton(fs.getPath("plan1.js")),
        getLogger(Level.INFO), execer);
    Map<String, Product> products = p.getProducts();
    assertEquals(
        (""
         + "{myProduct={"
           + "\"inputs\":[\"**.foo\"],"
           + "\"outputs\":[\"**.bar\"],"
           + "\"actions\":[{"
             + "\"tool\":\"myTool\","
             + "\"inputs\":[\"**.foo\"],"
             + "\"outputs\":[\"**.bar\"]"
           + "}]"
         + "}}"),
         "" + products);
    assertTrue(getLog().isEmpty());
  }

  public final void testBadToolReturnValue() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            ("    plan1.js \"({ foo: null })\""),
            ("    plan2.js \"({\\n"
             + "  myProduct: {\\n"
             + "    inputs: [ '**.foo' ],\\n"
             + "    outputs: [ '**.bar' ],\\n"
             + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
             + "  }\\n"
             + "})\"")));
    ArtifactValidityTracker files = new StubArtifactValidityTracker(
        fs.getPath("/cwd"));
    ToolProvider toolbox = new ToolProvider() {
      public List<Future<ToolSignature>> getAvailableToolSignatures() {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(new ToolSignature("myTool", null, null, true));
        return Collections.<Future<ToolSignature>>singletonList(f);
      }
      public void close() {}
    };
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Planner p = new Planner(
        files, toolbox, Arrays.asList(
            fs.getPath("plan1.js"),
            fs.getPath("plan2.js")),
        getLogger(Level.INFO), execer);
    Map<String, Product> products = p.getProducts();
    assertEquals(
        (""
         + "{myProduct={"
           + "\"inputs\":[\"**.foo\"],"
           + "\"outputs\":[\"**.bar\"],"
           + "\"actions\":[{"
             + "\"tool\":\"myTool\","
             + "\"inputs\":[\"**.foo\"],"
             + "\"outputs\":[\"**.bar\"]"
           + "}]"
         + "}}"),
         "" + products);
    assertEquals(
        Joiner.on('\n').join(
            "WARNING: Expected {\"inputs\":[<glob>, ...],...}, not null",
            "SEVERE: Failed to update plan plan1.js"),
        Joiner.on('\n').join(getLog()));
  }

  public final void testToolFileThrows() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
             + "  myProduct: {\\n"
             + "    inputs: [ '**.foo' ],\\n"
             + "    outputs: [ '**.bar' ],\\n"
             + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
             + "  }\\n"
             + "})\""),
             "    plan2.js \"throw {}\""));
    ArtifactValidityTracker files = new StubArtifactValidityTracker(
        fs.getPath("/cwd"));
    ToolProvider toolbox = new ToolProvider() {
      public List<Future<ToolSignature>> getAvailableToolSignatures() {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(new ToolSignature("myTool", null, null, true));
        return Collections.<Future<ToolSignature>>singletonList(f);
      }
      public void close() {}
    };
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Planner p = new Planner(
        files, toolbox, Arrays.asList(
            fs.getPath("plan1.js"),
            fs.getPath("plan2.js")),
        getLogger(Level.INFO), execer);
    Map<String, Product> products = p.getProducts();
    assertEquals(
        (""
         + "{myProduct={"
           + "\"inputs\":[\"**.foo\"],"
           + "\"outputs\":[\"**.bar\"],"
           + "\"actions\":[{"
             + "\"tool\":\"myTool\","
             + "\"inputs\":[\"**.foo\"],"
             + "\"outputs\":[\"**.bar\"]"
           + "}]"
         + "}}"),
         "" + products);
    assertEquals(
        Joiner.on('\n').join(
            "WARNING: Error executing plan plan2.js\n"
            + "org.mozilla.javascript.JavaScriptException:"
            + " [object Object] (/cwd/plan2.js#1)",
            "SEVERE: Failed to update plan plan2.js"),
        Joiner.on('\n').join(getLog()));
  }

  public final void testMalformedToolFile() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            ("    plan1.js \"({\\n"
             + "  myProduct: {\\n"
             + "    inputs: [ '**.foo' ],\\n"
             + "    outputs: [ '**.bar' ],\\n"
             + "    actions: [ tools.myTool(['**.foo'], ['**.bar']) ]\\n"
             + "  }\\n"
             + "})\""),
             "    plan2.js \"({ anotherTool: \""));  // JS syntax error
    ArtifactValidityTracker files = new StubArtifactValidityTracker(
        fs.getPath("/cwd"));
    ToolProvider toolbox = new ToolProvider() {
      public List<Future<ToolSignature>> getAvailableToolSignatures() {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(new ToolSignature("myTool", null, null, true));
        return Collections.<Future<ToolSignature>>singletonList(f);
      }
      public void close() {}
    };
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Planner p = new Planner(
        files, toolbox, Arrays.asList(
            fs.getPath("plan1.js"),
            fs.getPath("plan2.js")),
        getLogger(Level.INFO), execer);
    Map<String, Product> products = p.getProducts();
    assertEquals(
        (""
         + "{myProduct={"
           + "\"inputs\":[\"**.foo\"],"
           + "\"outputs\":[\"**.bar\"],"
           + "\"actions\":[{"
             + "\"tool\":\"myTool\","
             + "\"inputs\":[\"**.foo\"],"
             + "\"outputs\":[\"**.bar\"]"
           + "}]"
         + "}}"),
         "" + products);
    assertEquals(
        Joiner.on('\n').join(
            "WARNING: Error executing plan plan2.js\n"
            + "org.mozilla.javascript.EvaluatorException:"
            + " Unexpected end of file (/cwd/plan2.js#1)",
            "SEVERE: Failed to update plan plan2.js"),
        Joiner.on('\n').join(getLog()));
  }

  public final void testToolHelp() throws IOException {
    FileSystem fs = fileSystemFromAsciiArt(
        "/cwd",
        Joiner.on('\n').join(
            "/",
            "  cwd/",
            "    plan1.js \"help(tools.myTool); ({})\""));
    ArtifactValidityTracker files = new StubArtifactValidityTracker(
        fs.getPath("/cwd"));
    ToolProvider toolbox = new ToolProvider() {
      public List<Future<ToolSignature>> getAvailableToolSignatures() {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(new ToolSignature(
            "myTool", null,
            new Documentation(null, "A very useful tool", null),
            true));
        return Collections.<Future<ToolSignature>>singletonList(f);
      }
      public void close() {}
    };
    ScheduledExecutorService execer = new StubScheduledExecutorService();
    Planner p = new Planner(
        files, toolbox, Arrays.asList(
            fs.getPath("plan1.js")),
        getLogger(Level.INFO), execer);
    Map<String, Product> products = p.getProducts();
    assertTrue(products.isEmpty());
    assertEquals(
        "/cwd/plan1.js:1:INFO: Help: myTool\nA very useful tool",
        Joiner.on('\n').join(getLog()));
  }

  public final void testSanityChecker() {
    // TODO
  }

  public final void testPlanFileLoads() {
    // TODO
  }

  public final void testPlanFileLoadMissingFile() {
    // TODO
  }

  public final void testPlanFileTimeout() {
    // TODO
  }

  public final void testMaskingProductNames() {
    // TODO
  }

  public final void testToolFileUpdated() {
    // TODO
  }

  // TODO: test plan file that doesn't exist, but then is created.
}
