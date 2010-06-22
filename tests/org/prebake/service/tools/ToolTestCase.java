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

package org.prebake.service.tools;

import org.prebake.core.BoundName;
import org.prebake.core.Glob;
import org.prebake.core.GlobRelation;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.fs.FileAndHash;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.Loader;
import org.prebake.js.SimpleMembranableFunction;
import org.prebake.service.BuiltinResourceLoader;
import org.prebake.service.bake.JsOperatingSystemEnv;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.util.PbTestCase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Abstract base class for test cases that test built-in tools' ability to
 * decode options, deliver useful error messages, and compose a command line.
 * <p>
 * This test never executes anything on the command line so does not test a
 * tool's assumptions about the contracts of available executables.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class ToolTestCase extends PbTestCase {
  private final String testToolName;
  protected FileSystem fs;
  protected ToolTester tester;

  ToolTestCase(String toolName) {
    this.testToolName = toolName;
  }

  @Before public void setupFS() throws IOException {
    fs = fileSystemFromAsciiArt(
        "/cwd",
        "/",
        "  cwd/",
        "  root/",
        "  work/");
  }

  @Before public void setupTester() {
    tester = new ToolTester(testToolName);
  }

  @After public void teardownFS() throws IOException {
    if (fs != null) {
      fs.close();
      fs = null;
    }
  }

  @After public void checkTesterRun() {
    if (tester != null) {
      assertTrue(tester.wasRun());
      tester = null;
    }
  }

  @Test public final void testBuiltinHooks() {
    tester = null;
    // If this test fails, then you probably need to override stubToolHooks to
    // stub out whatever is in BuiltinToolHooks for your particular tool.
    assertEquals(
        BuiltinToolHooks.extraEnvironmentFor(testToolName).keySet(),
        stubToolHooks().keySet());
  }

  // Override to stub out system dependencies.
  protected ImmutableMap<String, ?> stubToolHooks() {
    return ImmutableMap.of();
  }

  protected class ToolTester {
    private final String toolName;
    private final ImmutableList.Builder<Glob> inputGlobs;
    private final ImmutableList.Builder<Glob> outputGlobs;
    private final ImmutableMap.Builder<String, Object> options;
    private final ImmutableList.Builder<String> inputPaths;
    private final ImmutableList.Builder<String> goldenLog;
    private boolean wasRun;

    private ToolTester(String toolName) {
      this.toolName = toolName;
      this.inputGlobs = ImmutableList.builder();
      this.outputGlobs = ImmutableList.builder();
      this.options = ImmutableMap.builder();
      this.inputPaths = ImmutableList.builder();
      this.goldenLog = ImmutableList.builder();
    }

    ToolTester withInput(Glob... glob) {
      this.inputGlobs.add(glob);
      return this;
    }

    ToolTester withOutput(Glob... glob) {
      this.outputGlobs.add(glob);
      return this;
    }

    ToolTester withOption(String name, Object value) {
      this.options.put(name, value);
      return this;
    }

    ToolTester withInputPath(String... path) {
      this.inputPaths.add(path);
      return this;
    }

    ToolTester expectLog(String... logMessages) {
      goldenLog.add(logMessages);
      return this;
    }

    ToolTester expectExec(int procId, String... command) {
      goldenLog.add(
          "Executed " + procId + " : "
          + JsonSink.stringify(Arrays.asList(command)));
      return this;
    }

    Executor.Output<Boolean> run() throws IOException {
      wasRun = true;
      final ImmutableList.Builder<String> log = ImmutableList.builder();
      ImmutableMap<String, ?> options = this.options.build();
      ImmutableGlobSet inputGlobSet = ImmutableGlobSet.of(inputGlobs.build());
      ImmutableGlobSet outputGlobSet = ImmutableGlobSet.of(outputGlobs.build());
      Action a = new Action(
          toolName, inputGlobSet, outputGlobSet, options);
      Product p = new Product(
          BoundName.fromString("p"), null,
          new GlobRelation(inputGlobSet, outputGlobSet),
          Collections.singletonList(a), false, null,
          fs.getPath("/root/plan.js"));
      Object os = JsOperatingSystemEnv.makeJsInterface(
          fs.getPath("/work"), new SimpleMembranableFunction(
              "stub exec", "exec", "process", "command", "arg0...") {
            int processId;
            public ImmutableMap<String, Object> apply(Object[] argv) {
              final int procId = ++processId;
              argv = flatten(argv);
              String cmd = (String) argv[0];
              if (cmd.startsWith("$$")) {
                assertNotNull(InVmProcess.Lookup.forCommand(cmd));
              }
              log.add(
                  "Executed " +  procId + " : "
                  + JsonSink.stringify(Arrays.asList(argv)));
              class ProcessHolder {
                final ImmutableMap<String, Object> jsObj
                    = ImmutableMap.<String, Object>builder()
                    .put("run", new SimpleMembranableFunction(
                         "stub run", "run", "this") {
                      boolean run;
                      public Object apply(Object[] args) {
                        if (!run) {
                          run = true;
                          assertEquals(0, args.length);
                          log.add("Running process " + procId);
                        }
                        return jsObj;
                      }
                    })
                    .put("waitFor", new SimpleMembranableFunction(
                         "stub waitFor", "waitFor", "result") {
                      public Object apply(Object[] args) {
                        assertEquals(0, args.length);
                        log.add("Waiting for process " + procId);
                        return 0;
                      }
                    })
                    .put("readFrom", new SimpleMembranableFunction(
                         "stub readFrom", "readFrom", "this", "path") {
                      public Object apply(Object[] args) {
                        assertEquals(1, args.length);
                        log.add(
                            "Process " + procId + " reading from "
                            + (String) args[0]);
                        return jsObj;
                      }
                    })
                    .put("writeTo", new SimpleMembranableFunction(
                         "stub writeTo", "writeTo", "this", "path") {
                      public Object apply(Object[] args) {
                        assertEquals(1, args.length);
                        log.add(
                            "Process " + procId + " writing to "
                            + (String) args[0]);
                        return jsObj;
                      }
                    })
                    .put("appendTo", new SimpleMembranableFunction(
                         "stub appendTo", "appendTo", "this", "path") {
                      public Object apply(Object[] args) {
                        assertEquals(1, args.length);
                        log.add(
                            "Process " + procId + " appending to "
                            + (String) args[0]);
                        return jsObj;
                      }
                    })
                    .put("pipeTo", new SimpleMembranableFunction(
                         "stub pipeTo", "pipeTo", "this", "process") {
                      public Object apply(Object[] args) {
                        assertEquals(1, args.length);
                        log.add(
                            "Process " + procId + " pipinging to "
                            + ((ImmutableMap<?, ?>) args[0]).get("__procId__"));
                        return jsObj;
                      }
                    })
                    .put("__procId__", procId)
                    .build();
              }
              return new ProcessHolder().jsObj;
            }
          });
      Executor.Output<Boolean> result = Executor.Factory.createJsExecutor().run(
          Boolean.class, getLogger(Level.INFO),
          new Loader() {
            public Executor.Input load(Path p) throws IOException {
              FileAndHash fh = BuiltinResourceLoader.tryLoad(p);
              if (fh == null) { throw new FileNotFoundException(p.toString()); }
              return Executor.Input.builder(
                  fh.getContentAsString(Charsets.UTF_8), p)
                  .build();
            }
          },
          Executor.Input.builder(
              Joiner.on('\n').join(
                  "var product = " + JsonSink.stringify(p) + ";",
                  "var action = " + JsonSink.stringify(a) + ";",
                  "tool.fire(inputs.slice(0), product, action, os)",
                  "    .run().waitFor() === 0"),
              "tool_bootstrap")
              .withActual("inputs", this.inputPaths.build())
              .withActual("os", os)
              .withActual("tool", Executor.Input.builder(
                  new InputStreamReader(
                      getClass().getResourceAsStream(toolName + ".js"),
                      Charsets.UTF_8),
                  fs.getPath("/--baked-in--/tools/" + toolName + ".js"))
                  .withActuals(getCommonJsEnv())
                  .withActuals(stubToolHooks())
                  .build())
              .build());
      for (String logMsg : ToolTestCase.this.getLog()) {
        // Matching JS line numbers makes the tests brittle, so normalize
        // log entries like "foo.js:44:INFO ..." to "foo.js:##:INFO ...".
        log.add(logMsg.replaceFirst(
            "^/--baked-in--/tools/([\\w-]+\\.js:)\\d+(:[A-Z])", "$1##$2"));
      }
      if (result.exit != null) {
        log.add("Threw " + result.exit.getCause());
      } else {
        log.add("Exited with " + result.result);
      }
      assertEquals(
          Joiner.on('\n').join(goldenLog.build()),
          Joiner.on('\n').join(log.build()));
      return result;
    }

    boolean wasRun() { return wasRun; }
  }

  private static Object[] flatten(Object... arr) {
    List<Object> flat = Lists.newArrayList();
    for (Object o : arr) {
      if (o instanceof Collection<?>) {
        flat.addAll((Collection<?>) o);
      } else {
        flat.add(o);
      }
    }
    return flat.toArray();
  }
}
