package org.prebake.service.plan;

import org.prebake.core.Glob;
import org.prebake.util.PbTestCase;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class PlanGrapherTest extends PbTestCase {
  private Path source;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    source = this.fileSystemFromAsciiArt("/", "foo").getPath("/foo");
  }

  public final void testPlanGraph() {
    PlanGrapher grapher = new PlanGrapher();
    grapher.update(product("foo", globs("*.a"), globs("*.b")));
    grapher.update(product("bar", globs("*.b"), globs("*.c")));
    grapher.update(product("baz", globs("*.c"), globs("*.d")));
    grapher.update(product("boo", globs("*.c"), globs("*.e")));
    grapher.update(product("far", globs("*.b", "*.d", "*.e"), globs("*.f")));

    {
      PlanGraph pg = grapher.snapshot();
      assertEquals("[bar, baz, boo, far, foo]", pg.nodes.toString());
      assertEquals(
          "{bar=[foo], baz=[bar], boo=[bar], far=[baz, boo, foo]}",
          pg.edges.toString());
    }

    grapher.update(product("baz", globs("*.b"), globs("*.e")));
    grapher.update(product("faz", globs("*.a"), globs("*.d")));

    {
      PlanGraph pg = grapher.snapshot();
      assertEquals("[bar, baz, boo, far, faz, foo]", pg.nodes.toString());
      assertEquals(
          "{bar=[foo], baz=[foo], boo=[bar], far=[baz, boo, faz, foo]}",
          pg.edges.toString());
    }
  }

  private Product product(String name, List<Glob> inputs, List<Glob> outputs) {
    return new Product(
        name, null, inputs, outputs,
        Collections.singletonList(new Action(
            "tool", inputs, outputs, ImmutableMap.<String, Object>of())),
        false, source);
  }

  private static List<Glob> globs(String... globs) {
    ImmutableList.Builder<Glob> b = ImmutableList.builder();
    for (String glob : globs) { b.add(Glob.fromString(glob)); }
    return b.build();
  }
}
