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

package org.prebake.service.bake;

import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.core.MessageQueue;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.Loader;
import org.prebake.js.SimpleMembranableFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.BuiltinToolHooks;
import org.prebake.service.tools.ToolContent;
import org.prebake.service.tools.ToolProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Kicks off processes based on actions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class Oven {
  private final OperatingSystem os;
  private final FileVersioner files;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final ToolProvider toolbox;
  private final Logger logger;

  Oven(OperatingSystem os, FileVersioner files,
       ImmutableMap<String, ?> commonJsEnv, ToolProvider toolbox,
       Logger logger) {
    this.os = os;
    this.commonJsEnv = commonJsEnv;
    this.files = files;
    this.toolbox = toolbox;
    this.logger = logger;
  }

  @Nonnull Executor.Output<Boolean> executeActions(
      final Path workingDir, Product p, List<Path> inputs,
      final List<Path> paths, final Hash.Builder hashes)
      throws IOException {
    List<String> inputStrs = Lists.newArrayList();
    for (Path input : inputs) { inputStrs.add(input.toString()); }
    Collections.sort(inputStrs);
    Executor execer = Executor.Factory.createJsExecutor();
    final WorkingFileChecker checker = new WorkingFileChecker(
        files.getVersionRoot(), workingDir);
    ExecFn execFn = new ExecFn(os, workingDir, checker, logger);
    ImmutableMap.Builder<String, Object> actuals = ImmutableMap.builder();
    actuals.putAll(commonJsEnv);
    actuals.put("os", JsOperatingSystemEnv.makeJsInterface(workingDir, execFn));
    actuals.put(
        "matching",
        // A function that finds inputs for actions.
        // We delay this instead of doing it in the action loop since
        // one action might produce files that are inputs to another.
        new SimpleMembranableFunction("", "matching", "paths", "globs") {
          MessageQueue mq = new MessageQueue();
          public ImmutableList<String> apply(Object[] argv) {
            ImmutableList<Glob> inputGlobs = Glob.CONV.convert(argv[0], mq);
            // Already vetted by Product parser.
            if (mq.hasErrors()) { throw new IllegalStateException(); }
            ImmutableList.Builder<String> actionInputs
                = ImmutableList.builder();
            try {
              for (Path actionInput : WorkingDir.matching(
                       workingDir, ImmutableSet.<Path>of(), inputGlobs)) {
                actionInputs.add(actionInput.toString());
              }
            } catch (IOException ex) {
              Throwables.propagate(ex);
            }
            return actionInputs.build();
          }
        });
    StringBuilder productJs = new StringBuilder();
    {
      Map<String, String> toolNameToLocalName = Maps.newHashMap();
      for (Action a : p.actions) {
        // First, load each tool module.
        String toolName = a.toolName;
        if (toolNameToLocalName.containsKey(toolName)) { continue; }
        String localName = "tool_" + toolNameToLocalName.size();
        toolNameToLocalName.put(toolName, localName);
        ToolContent tool = toolbox.getTool(toolName);
        if (tool.fh.getHash() != null) {
          paths.add(tool.fh.getPath());
          hashes.withHash(tool.fh.getHash());
        }
        ImmutableMap<String, ?> extraEnv = tool.isBuiltin
            ? BuiltinToolHooks.extraEnvironmentFor(toolName)
            : ImmutableMap.<String, Object>of();
        actuals.put(
            localName,
            Executor.Input.builder(
                tool.fh.getContentAsString(Charsets.UTF_8), tool.fh.getPath())
                .withActuals(commonJsEnv)
                .withActuals(extraEnv)
                .build());
      }
      JsonSink productJsSink = new JsonSink(productJs);
      // Second, store the product.
      productJsSink.write("var product = ").writeValue(p).write(";\n");
      productJsSink.write("product.name = ").writeValue(p.name).write(";\n");
      productJsSink.write("product = Object.frozenCopy(product);\n");
      // If the product wants to control how actions are executed, then wrap
      // the tool execution in a call to the mobile function product.bake.
      boolean thunkify = p.bake != null;
      // TODO: rework tool output so product.bake can actually pipe the result
      // of one tool to another.
      if (thunkify) { productJsSink.write("!!product.bake(["); }
      // Third, for each action, invoke its tool's run method with the proper
      // arguments.
      boolean firstAction = true;
      for (Action action : p.actions) {
        if (thunkify) {
          if (!firstAction) { productJsSink.write(","); }
          productJsSink.write("function () { return ");
        }
        productJsSink
            .write(toolNameToLocalName.get(action.toolName))
            .write(".fire(\n    ")
            .writeValue(action.options)
            .write(",\n    matching(").writeValue(action.inputs)
            .write("),\n    product,\n    ")
            .writeValue(action)
            .write(",\n    os);\n");
        if (thunkify) { productJsSink.write("}"); }
        firstAction = false;
      }
      if (thunkify) { productJsSink.write("]);"); }
    }
    Executor.Input src = Executor.Input.builder(
        productJs.toString(), "product-" + p.name)
        .withBase(workingDir)
        .withActuals(actuals.build())
        .build();
    // Set up output directories.
    {
      Set<Path> outPaths = Sets.newHashSet();
      for (Action a : p.actions) {
        for (Glob glob : a.outputs) {
          Path outPath = glob.getPathContainingAllMatches(workingDir);
          // We use 0700 since we're only operating in the working dir.
          if (outPaths.add(outPath)) { Baker.mkdirs(outPath, 0700); }
        }
      }
    }
    try {
      // Run the script.
      return execer.run(Boolean.class, logger, new Loader() {
        public Executor.Input load(Path p) throws IOException {
          FileAndHash fh = files.load(p);
          if (fh.getHash() != null) {
            paths.add(fh.getPath());
            hashes.withHash(fh.getHash());
          }
          return Executor.Input
              .builder(fh.getContentAsString(Charsets.UTF_8), p).build();
        }
      }, src);
    } finally {
      // We can't allow processes to keep mucking with the working directory
      // after we kill it and possibly recreate it for a rebuild.
      // If something wants to spawn long-lasting processes such as a
      // java compilation service, they can spawn a child process and disown it.
      execFn.killOpenProcesses();
    }
  }
}
