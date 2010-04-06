package org.prebake.service.plan;

import org.prebake.core.Hash;
import org.prebake.core.MessageQueue;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.ArtifactValidityTracker;
import org.prebake.fs.NonFileArtifact;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.Loader;
import org.prebake.js.YSON;
import org.prebake.js.YSONConverter;
import org.prebake.js.Executor.Input;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
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

/**
 * Keeps the set of {@link Product products} up-to-date.
 *
 * @author mikesamuel@gmail.com
 */
public final class Planner implements Closeable {
  private final Multimap<String, Product> productsByName = Multimaps
      .synchronizedMultimap(Multimaps.newMultimap(
          Maps.<String, Collection<Product>>newLinkedHashMap(),
          new Supplier<List<Product>>() {
            public List<Product> get() { return Lists.newArrayList(); }
          }));

  private final ImmutableMap<Path, PlanPart> planParts;
  private final ArtifactValidityTracker files;
  private final ToolProvider toolbox;
  private final Logger logger;
  private final ScheduledExecutorService execer;
  private final ArtifactAddresser<PlanPart> productAddresser
      = new ArtifactAddresser<PlanPart>() {
    public String addressFor(PlanPart artifact) {
      return artifact.planFile.toString();
    }
    public PlanPart lookup(String address) {
      return planParts.get(files.getFileSystem().getPath(address));
    }
  };
  private final Future<?> updater;

  public Planner(
      ArtifactValidityTracker files, ToolProvider toolbox,
      Iterable<Path> planFiles, Logger logger,
      ScheduledExecutorService execer) {
    this.files = files;
    this.toolbox = toolbox;
    this.logger = logger;
    this.execer = execer;
    ImmutableMap.Builder<Path, PlanPart> b = ImmutableMap.builder();
    for (Path p : planFiles) { b.put(p, new PlanPart(p)); }
    this.planParts = b.build();
    this.updater = execer.scheduleWithFixedDelay(new Runnable() {
      public void run() { getProductLists(); }
    }, 1000, 1000, TimeUnit.MILLISECONDS);
  }

  public void close() {
    updater.cancel(true);
  }

  public Map<String, Product> getProducts() {
    Map<String, Product> allProducts = Maps.newLinkedHashMap();
    for (Future<ImmutableList<Product>> pp : getProductLists()) {
      try {
        Iterable<Product> products = pp.get();
        if (products == null) { continue; }
        for (Product p : products) {
          if (!allProducts.containsKey(p.name)) {
            allProducts.put(p.name, p);
          } else {
            Product old = allProducts.put(p.name, null);
            logger.log(
                Level.WARNING, "Duplicate product {0} in {1}",
                new Object[] { p.name, p.source });
            if (old != null) {
              logger.log(
                  Level.WARNING, "Duplicate product {0} in {1}",
                  new Object[] { p.name, old.source });
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
    Iterator<Map.Entry<String, Product>> it = allProducts.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Product> e = it.next();
      if (e.getValue() == null) { it.remove(); }
    }
    return allProducts;
  }

  private List<Future<ImmutableList<Product>>> getProductLists() {
    String toolJs;
    boolean gotAllTools = true;
    try {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      sink.write(" var freeze = {}.constructor.freeze;")
          .write(" var frozenCopy = {}.constructor.frozenCopy;")
          .write("({");

      boolean sawOne = false;
      for (Future<ToolSignature> f : toolbox.getAvailableToolSignatures()) {
        try {
          ToolSignature sig = f.get();
          if (sig == null) {
            gotAllTools = false;
            continue;
          }
          if (sawOne) { sink.write(","); }
          sink.writeValue(sig.name).write(":freeze(");
          if (sig.help != null) {
            sink.write("withHelp(").writeValue(sig.help).write(",");
          } else {
            sink.write("(");
          }
          sink.write("function (inputs, outputs, options) {")
              // copy and freeze options, outputs, and inputs
              .write(" inputs = frozenCopy(inputs);")
              .write(" outputs = frozenCopy(outputs);")
              .write(" options = frozenCopy(options);");
          if (sig.productChecker != null) {
            sink.write("(").writeValue(sig.productChecker).write(")(options);");
          }
          sink.write(" return freeze({ tool: ")
              .writeValue(sig.name)
              .write(", outputs: outputs")
              .write(", inputs: inputs")
              .write(", options: options });")
              .write("}))");
          sawOne = true;
        } catch (ExecutionException ex) {
          logger.log(Level.SEVERE, "Tool not available", ex);
          gotAllTools = false;
        } catch (InterruptedException ex) {
          logger.log(Level.SEVERE, "Tool not available", ex);
          gotAllTools = false;
        }
      }
      sink.write("})");
      sink.close();
      toolJs = sb.toString();
    } catch (IOException ex) {
      throw new RuntimeException(ex);  // Writing to StringBuilder
    }

    if (!gotAllTools) {
      logger.log(Level.WARNING, "Planner could not retrieve all tools");
    }

    Executor.Input toolDef = Executor.Input.builder(toolJs, "tools").build();

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
          try {
            return derivePlan();
          } finally {
            pp.future = null;
          }
        }

        private ImmutableList<Product> derivePlan() {
          synchronized (pp) {
            if (pp.valid) { return pp.products; }
          }
          for (int nAttempts = 4; --nAttempts >= 0;) {
            List<Hash> hashes = Lists.newArrayList();
            List<Path> paths = Lists.newArrayList();
            try {
              Executor.Output<YSON> planFileOut = execPlan(
                  toolDef, pp, hashes, paths);
              if (planFileOut != null) {
                Object javaObj = planFileOut.result.toJavaObject();
                ImmutableList<Product> products = unpack(pp, javaObj);
                if (products != null) {
                  Hash.Builder allHashes = Hash.builder();
                  for (Hash hash : hashes) {
                    allHashes.withHash(hash);
                  }
                  synchronized (pp) {
                    if (files.update(
                            productAddresser, pp, paths, allHashes.build())) {
                      pp.products = products;
                      pp.valid = true;
                      return products;
                    }
                  }
                  logger.log(
                      Level.WARNING, "Version skew for {0}.  {1} attempts left",
                      new Object[] { pp.planFile, nAttempts });
                  continue;
                }
              }
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

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private Executor.Output<YSON> execPlan(
      Executor.Input toolDef, PlanPart pp,
      final List<Hash> hashes, final List<Path> paths)
      throws IOException {
    FileAndHash pf = files.load(pp.planFile);
    Hash h = pf.getHash();
    if (h != null) {
      hashes.add(h);
      paths.add(pp.planFile);
    }
    Executor planRunner;
    try {
      planRunner = Executor.Factory.createJsExecutor(
          Executor.Input.builder(pf.getContentAsString(UTF8), pf.getPath())
          .withActuals(Collections.singletonMap("tools", toolDef))
          .build());
    } catch (Executor.MalformedSourceException ex) {
      logger.log(Level.WARNING, "Failed to execute plan " + pp.planFile, ex);
      return null;
    }
    try {
      return planRunner.run(YSON.class, logger, new Loader() {
        public Input load(Path p) throws IOException {
          FileAndHash rf;
          try {
            rf = files.load(p);
          } catch (IOException ex) {
            // We need to depend on non-existent files in case they
            // are later created.
            paths.add(p);
            throw ex;
          }
          Hash h = rf.getHash();
          if (h != null) {
            paths.add(p);
            hashes.add(h);
          }
          return Executor.Input.builder(rf.getContentAsString(UTF8), p).build();
        }
      });
    } catch (Executor.AbnormalExitException ex) {
      logger.log(Level.WARNING, "Failed to execute plan " + pp.planFile, ex);
      return null;
    }
  }

  private ImmutableList<Product> unpack(PlanPart pp, Object scriptOutput) {
    MessageQueue mq = new MessageQueue();
    Map<String, Product> productMap = YSONConverter.Factory.mapConverter(
        YSONConverter.Factory.withType(String.class),
        // Use a temporary name, and then rename later
        Product.converter("tmp", pp.planFile))
        .convert(scriptOutput, mq);
    if (mq.hasErrors()) {
      for (String msg : mq.getMessages()) {
        logger.log(Level.WARNING, "'" + msg.replace("'", "''") + "'");
      }
      return null;
    }
    ImmutableList.Builder<Product> b = ImmutableList.builder();
    for (Map.Entry<String, Product> e : productMap.entrySet()) {
      b.add(e.getValue().withName(e.getKey()));
    }
    return b.build();
  }

  private final class PlanPart implements NonFileArtifact {
    final Path planFile;
    ImmutableList<Product> products;
    boolean valid;
    Future<ImmutableList<Product>> future;

    PlanPart(Path planFile) { this.planFile = planFile; }

    public synchronized void markValid(boolean valid) {
      this.valid = valid;
      if (!valid) {
        if (this.products != null) {
          List<Product> products = this.products;
          this.products = null;
          synchronized (productsByName) {
            for (Product p : products) {
              productsByName.remove(p.name, p);
            }
          }
        }
      }
    }
  }
}
