package org.prebake.service.plan;

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
  }

  public final void testBrokenTools() {
    // TODO
  }

  public final void testMalformedToolFile() {
    // TODO
  }

  public final void testFailingToolFile() {
    // TODO
  }

  public final void testBadReturnValue() {
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

  // TODO: test plan file that doesn't exist, but then is created.
}
