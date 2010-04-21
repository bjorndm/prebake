package org.prebake.js;

import org.prebake.util.PbTestCase;

import java.util.logging.Level;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import org.junit.Test;

public class CommonEnvironmentTest extends PbTestCase {
  @Test public final void testIntersect() {
    assertJsProduces("glob.intersect('foo/*/*.bar', '**/baz/*.bar')", true);
    assertJsProduces("glob.intersect('foo/*/*.bar', '**/baz/*.boo')", false);
    assertJsProduces("glob.intersect(['foo/*/*.bar'], '**/baz/*.bar')", true);
    assertJsProduces("glob.intersect(['foo/*/*.bar'], '**/baz/*.boo')", false);
  }

  @Test public final void testXform() {
    assertJsProduces(
        Joiner.on('\n').join(
            "var xf = glob.xformer('src/**.c', 'lib/**.o');",
            "[xf('src/foo/bar.c'), xf('src/baz.c'), xf('src/foo/bar.h')]"),
        Lists.newArrayList("lib/foo/bar.o", "lib/baz.o", null));
  }

  @Test public final void testSys() {
    assertJsProduces("sys.os.arch", "i386");
    assertJsProduces("sys.os.name", "generic-posix");
  }

  private void assertJsProduces(String js, Object result) {
    Executor.Output<?> out = Executor.Factory.createJsExecutor().run(
        Object.class, getLogger(Level.INFO), null,
        Executor.Input.builder(js, getName())
            .withActuals(getCommonJsEnv())
            .build());
    if (out.exit != null) { Throwables.propagate(out.exit); }
    assertEquals(result, out.result);
  }
}
