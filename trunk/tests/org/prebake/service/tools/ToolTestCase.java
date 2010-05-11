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

import org.prebake.core.Glob;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.SimpleMembranableFunction;
import org.prebake.service.bake.JsOperatingSystemEnv;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.util.PbTestCase;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;

/**
 * Abstract base class for test cases that test built-in tools' ability to
 * decode options, deliver useful error messages, and compose a command line.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class ToolTestCase extends PbTestCase {
  private final String toolName;
  protected FileSystem fs;
  protected ToolTester tester;

  ToolTestCase(String toolName) {
    this.toolName = toolName;
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
    tester = new ToolTester(toolName);
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

    ToolTester withInputPath(String path) {
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
      ImmutableList<Glob> inputGlobs = this.inputGlobs.build();
      ImmutableList<Glob> outputGlobs = this.outputGlobs.build();
      Action a = new Action(
          toolName, inputGlobs, outputGlobs, options);
      Product p = new Product(
          "p", null, inputGlobs, outputGlobs, Collections.singletonList(a),
          false, null, fs.getPath("/root/plan.js"));
      Object os = JsOperatingSystemEnv.makeJsInterface(
          fs.getPath("/work"), new SimpleMembranableFunction(
              "stub exec", "exec", "process", "command", "arg0...") {
            int processId;
            public ImmutableMap<String, Object> apply(Object[] argv) {
              final int procId = ++processId;
              log.add(
                  "Executed " +  procId + " : "
                  + JsonSink.stringify(Arrays.asList(argv)));
              class ProcessHolder {
                final ImmutableMap<String, Object> jsObj
                    = ImmutableMap.<String, Object>builder()
                    .put("run", new SimpleMembranableFunction(
                         "stub run", "run", "this") {
                      public Object apply(Object[] args) {
                        assertEquals(0, args.length);
                        log.add("Running process " + procId);
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
          Boolean.class, getLogger(Level.INFO), null,
          Executor.Input.builder(
              Joiner.on('\n').join(
                  "var product = " + JsonSink.stringify(p) + ";",
                  "var action = " + JsonSink.stringify(a) + ";",
                  "tool.fire(options, inputs.slice(0), product, action, os)",
                  "    .waitFor() === 0"),
              "tool_bootstrap")
              .withActual("options", options)
              .withActual("inputs", this.inputPaths.build())
              .withActual("os", os)
              .withActual("tool", Executor.Input.builder(
                  new InputStreamReader(
                      getClass().getResourceAsStream(toolName + ".js"),
                      Charsets.UTF_8),
                  toolName + ".js")
                  .withActuals(getCommonJsEnv())
                  .build())
              .build());
      log.addAll(ToolTestCase.this.getLog());
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
}
