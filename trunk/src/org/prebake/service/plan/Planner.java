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

package org.prebake.service.plan;

import org.prebake.core.ArtifactListener;
import org.prebake.core.BoundName;
import org.prebake.core.Hash;
import org.prebake.core.MessageQueue;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.fs.NonFileArtifact;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.YSON;
import org.prebake.js.YSONConverter;
import org.prebake.service.ArtifactDescriptors;
import org.prebake.service.BuiltinResourceLoader;
import org.prebake.service.LogHydra;
import org.prebake.service.Logs;
import org.prebake.service.PrebakeScriptLoader;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Keeps the set of {@link Product products} and the {@link PlanGrapher}
 * up-to-date.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Planner implements Closeable {
  /** Synchronized after plan parts. */
  private final Multimap<BoundName, Product> productsByName = Multimaps
      .synchronizedMultimap(Multimaps.newMultimap(
          Maps.<BoundName, Collection<Product>>newLinkedHashMap(),
          new ListSupplier<Product>()));

  private final ImmutableMap<Path, PlanPart> planParts;
  private final FileVersioner files;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final ToolProvider toolbox;
  private final Logs logs;
  private final ScheduledExecutorService execer;
  private final ArtifactAddresser<PlanPart> productAddresser
      = new ArtifactAddresser<PlanPart>() {
    public String addressFor(PlanPart artifact) {
      return artifact.normPath;
    }
    public PlanPart lookup(String address) {
      return planParts.get(files.getFileSystem().getPath(address));
    }
  };
  private final Future<?> updater;
  private final ArtifactListener<Product> listener;
  private final PlanGrapher grapher = new PlanGrapher();

  /**
   * @param files versions plan files.
   * @param commonJsEnv symbols available to plan file and tool file JavaScript.
   * @param toolbox defines the tools available to plan files.
   * @param logs receive messages about product definitions.
   * @param listener receives updates as products are defined or destroyed.
   * @param execer an executor which is used to schedule periodic maintenance
   *     tasks and which is used to update product definitions.
   */
  public Planner(
      FileVersioner files, ImmutableMap<String, ?> commonJsEnv,
      ToolProvider toolbox, Iterable<Path> planFiles, Logs logs,
      ArtifactListener<Product> listener, ScheduledExecutorService execer) {
    this.files = files;
    this.commonJsEnv = commonJsEnv;
    this.toolbox = toolbox;
    this.logs = logs;
    this.execer = execer;
    ImmutableMap.Builder<Path, PlanPart> b = ImmutableMap.builder();
    for (Path p : planFiles) { b.put(p, new PlanPart(p)); }
    this.planParts = b.build();
    this.updater = execer.scheduleWithFixedDelay(new Runnable() {
      public void run() { getProductLists(); }
    }, 1000, 1000, TimeUnit.MILLISECONDS);
    this.listener = ArtifactListener.Factory.chain(
        grapher.productListener, listener);
  }

  /** Tears down non-local state. */
  public void close() {
    updater.cancel(true);
    for (PlanPart pp : planParts.values()) {
      synchronized (pp) {
        if (pp.future != null) {
          pp.future.cancel(true);
          pp.future = null;
        }
      }
    }
  }

  /**
   * Gets a map of names to products.  This makes a best-effort to ensure that
   * products are up-to-date.
   * The output will not include any products that are masked -- that are named
   * in two or more plan files that run to completion.
   */
  public Map<BoundName, Product> getProducts() {
    Logger logger = logs.logger;
    Map<BoundName, Product> allProducts = Maps.newHashMap();
    for (Future<ImmutableList<Product>> pp : getProductLists()) {
      try {
        Iterable<Product> products = pp.get();
        if (products == null) { continue; }
        for (Product p : products) {
          if (!allProducts.containsKey(p.name)) {
            allProducts.put(p.name, p);
          } else {
            Product old = allProducts.put(p.name, null);
            if (old != null) {
              logger.log(
                  Level.WARNING, "Duplicate product {0} in {1} and {2}",
                  new Object[] { p.name, p.source, old.source });
            }
          }
        }
      } catch (ExecutionException ex) {
        logger.log(Level.WARNING, "Failed to evaluate plan", ex);
      } catch (InterruptedException ex) {
        logger.log(Level.WARNING, "Failed to evaluate plan", ex);
        break;
      }
    }
    Iterator<Map.Entry<BoundName, Product>> it
        = allProducts.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<BoundName, Product> e = it.next();
      if (e.getValue() == null) { it.remove(); }
    }
    // Now, hide nondeterminism from clients by producing a reliable key order.
    ImmutableMap.Builder<BoundName, Product> b = ImmutableMap.builder();
    BoundName[] keys = allProducts.keySet().toArray(
        new BoundName[allProducts.size()]);
    Arrays.sort(keys);
    for (BoundName key : keys) { b.put(key, allProducts.get(key)); }
    return b.build();
  }

  /**
   * A snapshot of the product graph.  This does not wait for products to be
   * brought up-to-date.
   */
  public PlanGraph getPlanGraph() { return grapher.snapshot(); }

  private List<Future<ImmutableList<Product>>> getProductLists() {
    Logger logger = logs.logger;
    // TODO: instead create an input so each tool's validator is in its own
    // appropriately named file to keep stack traces informative.
    String toolJs;
    boolean gotAllTools = true;
    try {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      sink.write("var freeze = {}.constructor.freeze;\n")
          .write("var frozenCopy = {}.constructor.frozenCopy;\n")
          .write("function withHelp(help, o) {\n")
          .write("  ({}).constructor.defineProperty(\n")
          .write("      o, 'help_',\n")
          .write("      { value: frozenCopy(help), enumerable: false });\n")
          .write("  return o;\n")
          .write("}\n")
          .write("({");

      boolean sawOne = false;
      for (Future<ToolSignature> f : toolbox.getAvailableToolSignatures()) {
        try {
          ToolSignature sig = f.get();
          if (sig == null) {
            gotAllTools = false;
            continue;
          }
          if (sawOne) { sink.write(",\n"); }
          sink.writeValue(sig.name).write(":freeze(");
          if (sig.help != null) {
            sink.write("withHelp(").writeValue(sig.help).write(", ");
          } else {
            sink.write("(");
          }
          sink.write("function ").write(sig.name)
              .write("(inputs, outputs, options) {\n")
              // copy and freeze options, outputs, and inputs
              .write("    if ('string' === typeof inputs) {\n")
              .write("      inputs = [inputs];\n")
              .write("    }\n")
              .write("    if ('string' === typeof outputs) {\n")
              .write("      outputs = [outputs];\n")
              .write("    }\n")
              .write("    inputs = frozenCopy(inputs);\n")
              .write("    outputs = frozenCopy(outputs);\n")
              .write("    options = frozenCopy(options);\n")
              .write("    var action = freeze({ tool: ").writeValue(sig.name)
              .write(", outputs: outputs")
              .write(", inputs: inputs")
              .write(", options: options });\n");
          if (sig.productChecker != null) {
            sink.write("    (").writeValue(sig.productChecker)
                .write(")(action);\n");
          }
          sink.write("    return action;\n")
              .write("  }))\n");
          sawOne = true;
        } catch (ExecutionException ex) {
          logger.log(Level.SEVERE, "Tool not available", ex);
          gotAllTools = false;
        } catch (InterruptedException ex) {
          logger.log(Level.SEVERE, "Tool not available", ex);
          gotAllTools = false;
        }
      }
      sink.write("})\n");
      sink.close();
      toolJs = sb.toString();
      logger.log(Level.FINER, "{0}", toolJs);
    } catch (IOException ex) {
      Throwables.propagate(ex);  // Writing to StringBuilder
      return Collections.emptyList();
    }

    if (!gotAllTools) {
      logger.log(Level.WARNING, "Planner could not retrieve all tools");
    }

    Executor.Input toolDef = Executor.Input.builder(
        toolJs,
        BuiltinResourceLoader.getBuiltinResourceRoot(files.getVersionRoot())
            .resolve("tools"))
        .withActuals(commonJsEnv).build();

    List<Future<ImmutableList<Product>>> out = Lists.newArrayList();
    for (PlanPart pp : planParts.values()) {
      Future<ImmutableList<Product>> products = requirePlanPart(toolDef, pp);
      if (products != null) { out.add(products); }
    }
    return out;
  }

  private Future<ImmutableList<Product>> requirePlanPart(
      final Executor.Input toolDef, final PlanPart pp) {
    synchronized (pp) {
      if (pp.future != null) { return pp.future; }
      return pp.future = execer.submit(new Callable<ImmutableList<Product>>() {
        public ImmutableList<Product> call() throws Exception {
          synchronized (pp) {
            if (pp.valid) { return pp.products; }
          }
          String artifactDescriptor = ArtifactDescriptors.forPlanFile(
              pp.normPath);
          try {
            logs.logHydra.artifactProcessingStarted(
                artifactDescriptor,
                EnumSet.of(LogHydra.DataSource.SERVICE_LOGGER));
          } catch (IOException ex) {
            logs.logger.log(Level.SEVERE, "Failed to open log file", ex);
          }
          try {
            return derivePlan();
          } finally {
            logs.logHydra.artifactProcessingEnded(artifactDescriptor);
            // TODO: why is the log file not written?
          }
        }

        private ImmutableList<Product> derivePlan() {
          Logger logger = logs.logger;
          for (int nAttempts = 4; --nAttempts >= 0;) {
            long t0 = logs.highLevelLog.getClock().nanoTime();
            Hash.Builder hashes = Hash.builder();
            ImmutableList.Builder<Path> paths = ImmutableList.builder();
            try {
              Executor.Output<YSON> planFileOut = execPlan(
                  toolDef, pp, hashes, paths);
              if (planFileOut.exit == null) {
                Object javaObj = planFileOut.result.toJavaObject();
                ImmutableList<Product> products = unpack(pp, javaObj);
                if (products != null) {
                  boolean isValid;
                  synchronized (pp) {
                    isValid = files.updateArtifact(
                        productAddresser, pp, new PlannerResult(t0, products),
                        paths.build(), hashes.build());
                  }
                  if (isValid) {
                    logger.log(
                        Level.INFO, "Plan file {0} is up to date",
                        pp.planFile);
                    return products;
                  }
                  logger.log(
                      Level.WARNING, "Version skew for {0}.  {1} attempts left",
                      new Object[] { pp.planFile, nAttempts });
                  continue;
                }
              } else {
                logger.log(
                    Level.WARNING, "Failed to execute plan " + pp.planFile,
                    planFileOut.exit);
              }
            } catch (FileNotFoundException ex) {
              logger.log(
                  Level.WARNING, "Missing plan " + pp.planFile, ex);
            } catch (IOException ex) {
              logger.log(
                  Level.WARNING, "Error executing plan " + pp.planFile, ex);
            } catch (RuntimeException ex) {
              logger.log(
                  Level.WARNING, "Error executing plan " + pp.planFile, ex);
            }
            break;
          }
          logger.log(Level.SEVERE, "Failed to update plan {0}", pp.planFile);
          return null;
        }
      });
    }
  }

  private @Nonnull Executor.Output<YSON> execPlan(
      Executor.Input toolDef, PlanPart pp,
      Hash.Builder hashes, ImmutableList.Builder<Path> paths)
      throws IOException {
    FileAndHash pf = files.load(pp.planFile);
    Hash h = pf.getHash();
    if (h != null) {
      hashes.withHash(h);
      paths.add(pp.planFile);
    }
    Executor planRunner = Executor.Factory.createJsExecutor();
    return planRunner.run(
        YSON.class, logs.logger, new PrebakeScriptLoader(files, paths, hashes),
        Executor.Input.builder(
            pf.getContentAsString(Charsets.UTF_8), pf.getPath())
        .withActuals(commonJsEnv)
        .withActual("tools", toolDef).build());
  }

  private static final BoundName TMP_IDENT = BoundName.fromString(
      "tmp");
  private ImmutableList<Product> unpack(PlanPart pp, Object scriptOutput) {
    MessageQueue mq = new MessageQueue();
    Map<BoundName, Product> productMap
        = YSONConverter.Factory.mapConverter(
            YSONConverter.Factory.withType(
                BoundName.class, "a bound name"),
            // Use a temporary name, and then rename later
            Product.converter(TMP_IDENT, pp.planFile))
            .convert(scriptOutput, mq);
    if (mq.hasErrors()) {
      Logger logger = logs.logger;
      for (String msg : mq.getMessages()) {
        logger.log(Level.WARNING, MessageQueue.escape(msg));
      }
      return null;
    }
    ImmutableList.Builder<Product> b = ImmutableList.builder();
    for (Map.Entry<BoundName, Product> e : productMap.entrySet()) {
      b.add(e.getValue().withName(e.getKey()));
    }
    return b.build();
  }

  private final class PlanPart implements NonFileArtifact<PlannerResult> {
    final Path planFile;
    final String normPath;
    ImmutableList<Product> products;
    boolean valid;
    Future<ImmutableList<Product>> future;

    PlanPart(Path planFile) {
      this.planFile = planFile;
      String normPath = files.getVersionRoot().relativize(planFile).toString();
      String sep = planFile.getFileSystem().getSeparator();
      if (!"/".equals(sep)) { normPath = normPath.replace(sep, "/"); }
      this.normPath = normPath;
    }

    public void invalidate() {
      synchronized (this) {
        this.valid = false;
        List<Product> products = this.products;
        if (products != null) {
          this.products = null;
          if (future != null) {
            future.cancel(false);
            future = null;
          }
          synchronized (productsByName) {
            for (Product p : products) {
              productsByName.remove(p.name, p);
              Collection<Product> prods = productsByName.get(p.name);
              switch (prods.size()) {
                case 0:
                  // No more products with this name left.
                  listener.artifactDestroyed(p.name.ident);
                  break;
                case 1:
                  // Previously the product was masked, but no longer.
                  listener.artifactChanged(prods.iterator().next());
                  break;
                default: break;  // Still masked.
              }
            }
          }
        }
      }
      logs.highLevelLog.planStatusChanged(
          logs.highLevelLog.getClock().nanoTime(), normPath, false);
    }

    public void validate(PlannerResult result) {
      ImmutableList<Product> products = result.products;
      synchronized (this) {
        this.valid = true;
        this.products = products;
      }
      for (Product p : products) { listener.artifactChanged(p); }
      logs.highLevelLog.planStatusChanged(result.t0, normPath, true);
    }
  }

  private static final class ListSupplier<T> implements Supplier<List<T>> {
    public List<T> get() { return Lists.newArrayList(); }
  }

  private static final class PlannerResult {
    final long t0;
    final ImmutableList<Product> products;

    PlannerResult(long t0, ImmutableList<Product> products) {
      this.t0 = t0;
      this.products = products;
    }
  }
}
