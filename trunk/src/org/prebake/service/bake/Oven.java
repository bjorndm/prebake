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

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.Loader;
import org.prebake.js.MembranableFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolProvider;

import java.io.IOError;
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
import com.google.common.collect.ImmutableMap;
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
  private final int umask;
  private final Logger logger;

  Oven(OperatingSystem os, FileVersioner files,
       ImmutableMap<String, ?> commonJsEnv, ToolProvider toolbox, int umask,
       Logger logger) {
    this.os = os;
    this.commonJsEnv = commonJsEnv;
    this.files = files;
    this.toolbox = toolbox;
    this.logger = logger;
    this.umask = umask;
  }

  @Nonnull Executor.Output<Boolean> executeActions(
      final Path workingDir, Product p, List<Path> inputs,
      final List<Path> paths, final Hash.Builder hashes)
      throws IOException {
    List<String> inputStrs = Lists.newArrayList();
    for (Path input : inputs) { inputStrs.add(input.toString()); }
    Collections.sort(inputStrs);
    Executor exec = Executor.Factory.createJsExecutor();
    MembranableFunction execFn = new MembranableFunction() {
      public Object apply(Object[] args) {
        // TODO: check inside args for clientDir
        if (args.length == 0 || args[0] == null) {
          throw new IllegalArgumentException("No command specified");
        }
        String cmd = (String) args[0];
        List<String> argv = Lists.newArrayList();
        for (int i = 1, n = args.length; i < n; ++i) {
          if (args[i] != null) { argv.add((String) args[i]); }
        }
        try {
          Process p = os.run(
              workingDir, cmd, argv.toArray(new String[argv.size()]));
          p.getOutputStream().close();
          return Integer.valueOf(p.waitFor());
        } catch (IOException ex) {
          throw new IOError(ex);
        } catch (InterruptedException ex) {
          Throwables.propagate(ex);
          return 0;
        }
      }
      public int getArity() { return 1; }
      public String getName() { return "exec"; }
      public Documentation getHelp() {
        return new Documentation(
            "exec(cmd, argv...)", "kicks off a command line process",
          "prebake@prebake.org");
      }
    };
    ImmutableMap.Builder<String, Object> actuals = ImmutableMap.builder();
    actuals.putAll(commonJsEnv);
    actuals.put("exec", execFn);
    StringBuilder productJs = new StringBuilder();
    {
      JsonSink productJsSink = new JsonSink(productJs);
      Map<String, String> toolNameToLocalName = Maps.newHashMap();
      for (Action a : p.actions) {
        // First, load each tool module.
        String toolName = a.toolName;
        if (toolNameToLocalName.containsKey(toolName)) { continue; }
        String localName = "tool_" + toolNameToLocalName.size();
        toolNameToLocalName.put(toolName, localName);
        FileAndHash tool = toolbox.getTool(toolName);
        if (tool.getHash() != null) {
          paths.add(tool.getPath());
          hashes.withHash(tool.getHash());
        }
        actuals.put(
            localName,
            Executor.Input.builder(
                tool.getContentAsString(Charsets.UTF_8), tool.getPath())
                .withActuals(commonJsEnv)
                .build());
      }
      // Second, store the product.
      productJsSink.write("var product = ").writeValue(p).write(";\n");
      productJsSink.write("product.name = ").writeValue(p.name).write(";\n");
      productJsSink.write("product = Object.frozenCopy(product);\n");
      // Third, for each action, invoke its tool's run method with the proper
      // arguments.
      for (Action action : p.actions) {
        productJsSink
            .write(toolNameToLocalName.get(action.toolName))
            .write(".fire(\n    ")
            .writeValue(action.options).write(",\n    ")
            // TODO: define exec based on os
            // TODO: fetch the inputs based on the action's input globs
            // and
            .writeValue(inputStrs)
            .write(",\n    product,\n    ")
            .writeValue(action)
            .write(",\n    exec);\n");
      }
      productJsSink.writeValue(true);
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
          if (outPaths.add(outPath)) { Baker.mkdirs(outPath, umask); }
        }
      }
    }
    // Run the script.
    return exec.run(Boolean.class, logger, new Loader() {
      public Executor.Input load(Path p) throws IOException {
        FileAndHash fh = files.load(p);
        if (fh.getHash() != null) {
          paths.add(fh.getPath());
          hashes.withHash(fh.getHash());
        }
        return Executor.Input.builder(fh.getContentAsString(Charsets.UTF_8), p)
            .build();
      }
    }, src);
  }
}
