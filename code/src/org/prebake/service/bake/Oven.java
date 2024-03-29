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
import org.prebake.core.ImmutableGlobSet;
import org.prebake.core.MessageQueue;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.SimpleMembranableFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.service.PrebakeScriptLoader;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.BuiltinToolHooks;
import org.prebake.service.tools.ToolContent;
import org.prebake.service.tools.ToolProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.google.common.base.Charsets;
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
  private final ExecutorService execService;
  private final Logger logger;

  Oven(OperatingSystem os, FileVersioner files,
       ImmutableMap<String, ?> commonJsEnv, ToolProvider toolbox,
       ExecutorService execService, Logger logger) {
    this.os = os;
    this.commonJsEnv = commonJsEnv;
    this.files = files;
    this.toolbox = toolbox;
    this.execService = execService;
    this.logger = logger;
  }

  @Nonnull Executor.Output<Boolean> executeActions(
      final Path workingDir, Product p,
      final ImmutableList.Builder<Path> paths, final Hash.Builder hashes)
      throws IOException {
    Executor execer = Executor.Factory.createJsExecutor();
    final WorkingFileChecker checker = new WorkingFileChecker(
        files.getVersionRoot(), workingDir);
    ExecFn execFn = new ExecFn(os, workingDir, checker, execService, logger);
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
            ImmutableGlobSet inputGlobSet = ImmutableGlobSet.of(inputGlobs);
            List<Path> inputs;
            try {
              inputs = Lists.newArrayList(WorkingDir.matching(
                  workingDir, ImmutableSet.<Path>of(), inputGlobSet));
            } catch (IOException ex) {
              throw new RuntimeException(ex);
            }
            sortByGlobs(inputs, inputGlobSet);
            ImmutableList.Builder<String> b = ImmutableList.builder();
            for (Path p : inputs) { b.add(p.toString()); }
            return b.build();
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
      productJsSink.write("(");
      // If there is a product.bake method, then we do something like
      //   product.bake([
      //       function () { return /* invoke action 0 */ },
      //       function () { return /* invoke action 1 */ },
      //       ...]);
      // so the result of the process is the result of product.bake which gets
      // a list of thunks : one per action.
      //
      // Otherwise, we provide a default bake which runs and waits for each in
      // order like:
      //   ((/* invoke action 0 */).run().waitFor() === 0
      //    && (/* invoke.action 1 */).run().waitFor() === 0
      //    && ...);
      if (thunkify) { productJsSink.write("product.bake(["); }
      if (!thunkify && p.actions.isEmpty()) { productJsSink.write("true"); }
      // Third, for each action, invoke its tool's run method with the proper
      // arguments.
      for (int i = 0, n = p.actions.size(); i < n; ++i) {
        if (i != 0) { productJsSink.write(thunkify ? "," : ") && ("); }
        if (thunkify) {
          productJsSink.write("function () { return ");
        }
        // Fill in the /* invoke action x */ bits above with a call to the
        // tool like:
        //   tool_x.fire(matching(['foo/*.c']), product, product.actions[0], os)
        // where product is a frozen YSON object.
        // Matching is defined above, and os is defined in JsOperatingSystemEnv.
        Action action = p.actions.get(i);
        productJsSink
            .write(toolNameToLocalName.get(action.toolName))
            .write(".fire(matching(");
        action.inputs.toJson(productJsSink);
        // .slice(0) works around problem where membraned arrays don't
        // behave as arrays w.r.t. concat and other builtins.
        productJsSink.write(").slice(0),\n    product,\n    product.actions[")
            .write(String.valueOf(i))
            .write("],\n    os)\n");
        productJsSink.write(thunkify ? "}" : ".run().waitFor() === 0");
      }
      if (thunkify) { productJsSink.write("])"); }
      productJsSink.write(");");
    }
    Executor.Input src = Executor.Input.builder(
        productJs.toString(), "product-" + p.name)
        // TODO: use the product plan file as the base dir so that loads in the
        // product's bake function happen relative to the plan file.
        // This requires us to take the source path into account when deciding
        // whether a product has changed since last build.
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
    Executor.Output<Boolean> runResult;
    try {
      // Run the script.
      runResult = execer.run(
          Boolean.class, logger, new PrebakeScriptLoader(files, paths, hashes),
          src);
    } finally {
      // We can't allow processes to keep mucking with the working directory
      // after we kill it and possibly recreate it for a rebuild.
      // If something wants to spawn long-lasting processes such as a
      // java compilation service, they can spawn a child process and disown it.
      if (execFn.killOpenProcesses()) {
        runResult = new Executor.Output<Boolean>(false, false, null);
      }
    }
    return runResult;
  }

  private static final class TaggedPath {
    final int index;
    final Path p;
    TaggedPath(int index, Path p) {
      this.index = index;
      this.p = p;
    }
  }
  private static void sortByGlobs(List<Path> paths, ImmutableGlobSet globs) {
    int n = paths.size();
    Map<Glob, Integer> globByIndex = Maps.newIdentityHashMap();
    int nGlobs = 0;
    for (Glob g : globs) { globByIndex.put(g, nGlobs++); }
    if (nGlobs < 2) { return; }
    TaggedPath[] taggedPaths = new TaggedPath[n];
    for (int i = 0; i < n; ++i) {
      int index = nGlobs;
      for (Glob g : globs.matching(paths.get(i))) {
        int k = globByIndex.get(g);
        if (k < index) { index = k; }
      }
      taggedPaths[i] = new TaggedPath(index, paths.get(i));
    }
    Arrays.sort(
        taggedPaths,
        new Comparator<TaggedPath>() {
          public int compare(TaggedPath p, TaggedPath q) {
            return p.index - q.index;
          }
        });
    for (int i = n; --i >= 0;) { paths.set(i, taggedPaths[i].p); }
  }
}
