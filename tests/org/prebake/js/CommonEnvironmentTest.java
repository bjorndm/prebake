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

import org.prebake.util.PbTestCase;

import java.util.logging.Level;

import javax.annotation.Nullable;

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
        Lists.newArrayList("lib/foo/bar.o", "lib/baz.o", null), false);
    assertJsProduces(
        Joiner.on('\n').join(
            "var xf = glob.xformer('src/**.c', 'lib/**.o');",
            "[xf('src/foo/bar.c'), xf('src/baz.c'), xf('src/foo/bar.h')]"),
        Lists.newArrayList("lib\\foo\\bar.o", "lib\\baz.o", null), true);
  }

  @Test public final void testPrefix() {
    assertJsProduces("glob.prefix('lib/**.class')", "lib/");
    assertJsProduces(
        "glob.prefix('lib/org/**.class', 'lib/com/**.class')", "lib/");
    assertJsProduces(
        "glob.prefix(['lib/org/**.class', 'lib/com/**.class'])", "lib/");
    assertJsProduces(
        "glob.prefix(['www/org/**.css', 'www/org/**.js'])", "www/org/", false);
    assertJsProduces(
        "glob.prefix(['www/org/**.css', 'www/org/**.js'])", "www\\org\\", true);
  }

  @Test public final void testMatcher() {
    assertJsProduces("glob.matcher('foo/**')('foo/bar')", true);
    assertJsProduces("glob.matcher('foo/**')('baz/bar')", false);
    assertJsProduces("glob.matcher('foo/**', 'baz/*')('baz/bar')", true);
    assertJsProduces("glob.matcher('foo/**', 'baz/*')('foo/bar')", true);
    assertJsProduces("glob.matcher('foo/**', 'baz/*')('baz/boo/far')", false);
  }

  @Test public final void testRootOf() {
    assertJsProduces("glob.rootOf('foo///*/*.bar', 'foo///**.bar')", "foo");
    assertJsProduces("glob.rootOf('foo/*/*.bar', 'foo///**.bar')", null);
    assertJsProduces("glob.rootOf('src///org/prebake')", "src");
    assertJsProduces("glob.rootOf('bar/baz///boo/*')", "bar/baz", false);
    assertJsProduces("glob.rootOf('bar/baz///boo/*')", "bar\\baz", true);
  }

  @Test public final void testSys() {
    assertJsProduces("sys.os.arch", "i386");
    assertJsProduces("sys.os.name", "generic-posix");
  }

  private void assertJsProduces(String js, @Nullable Object result) {
    assertJsProduces(js, result, false);
  }

  private void assertJsProduces(
      String js, @Nullable Object result, boolean dosPrefs) {
    Executor.Output<?> out = Executor.Factory.createJsExecutor().run(
        Object.class, getLogger(Level.INFO), null,
        Executor.Input.builder(js, getName())
            .withActuals(getCommonJsEnv(dosPrefs))
            .build());
    if (out.exit != null) { Throwables.propagate(out.exit); }
    assertEquals(result, out.result);
  }
}
