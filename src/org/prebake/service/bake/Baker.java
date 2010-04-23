package org.prebake.service.bake;

import org.prebake.channel.FileNames;
import org.prebake.core.ArtifactListener;
import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.GlobSet;
import org.prebake.core.Hash;
import org.prebake.fs.FileVersioner;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FilePerms;
import org.prebake.fs.GlobUnion;
import org.prebake.fs.NonFileArtifact;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.js.Loader;
import org.prebake.js.MembranableFunction;
import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ValueFuture;

/**
 * Maintains a set of products and whether they're up to date.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Baker {
  private final OperatingSystem os;
  private final FileVersioner files;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final Logger logger;
  private final ScheduledExecutorService execer;
  private final ConcurrentHashMap<String, ProductStatus> productStatuses
      = new ConcurrentHashMap<String, ProductStatus>();
  private final ConcurrentHashMap<String, ProductStatusChain> toolDeps
      = new ConcurrentHashMap<String, ProductStatusChain>();
  private ToolProvider toolbox;
  private final ArtifactAddresser<ProductStatus> addresser
      = new ArtifactAddresser<ProductStatus>() {
    public String addressFor(ProductStatus artifact) { return artifact.name; }
    public ProductStatus lookup(String address) {
      return productStatuses.get(address);
    }
  };
  private final int umask;

  public Baker(
      OperatingSystem os, FileVersioner files,
      ImmutableMap<String, ?> commonJsEnv, int umask, Logger logger,
      ScheduledExecutorService execer) {
    this.os = os;
    this.files = files;
    this.commonJsEnv = commonJsEnv;
    this.umask = umask;
    this.logger = logger;
    this.execer = execer;
  }

  public void setToolBox(ToolProvider toolbox) { this.toolbox = toolbox; }

  public Future<Boolean> bake(final String productName) {
    assert toolbox != null;
    final ProductStatus status = productStatuses.get(productName);
    if (status == null) {
      logger.log(Level.WARNING, "Unrecognized product {0}", productName);
      ValueFuture<Boolean> vf = ValueFuture.create();
      vf.set(Boolean.FALSE);
      return vf;
    }
    synchronized (status) {
      if (status.getBuildFuture() == null) {
        final Product product = status.getProduct();
        Future<Boolean> f = execer.submit(new Callable<Boolean>() {
          public Boolean call() {
            return build();
          }

          private boolean build() {
            logger.log(Level.INFO, "Starting bake of product {0}", productName);
            final Path workDir;
            final ImmutableList<Path> inputs;
            boolean passed = false;

            try {
              inputs = sortedFilesMatching(product.inputs);
              workDir = createWorkingDirectory(product.name);
              try {
                final List<Path> paths = Lists.newArrayList();
                final Hash.Builder hashes = Hash.builder();

                Set<Path> workingDirInputs = Sets.newLinkedHashSet();
                copyToWorkingDirectory(inputs, workDir, workingDirInputs);
                Executor.Output<Boolean> result = executeActions(
                    workDir, product, inputs, paths, hashes);
                if (Boolean.TRUE.equals(result.result)) {
                  ImmutableList<Path> outputs;
                  outputs = copyToRepo(
                      product.name, workDir, workingDirInputs, product.outputs);
                  files.update(outputs);
                  synchronized (status) {
                    if (status.product.equals(product)
                        && files.update(
                            addresser, status, paths, hashes.build())) {
                      passed = true;
                    } else {
                      logger.log(
                          Level.WARNING, "Version skew for " + product.name);
                    }
                  }
                } else {
                  if (result.exit != null) {
                    logger.log(
                        Level.SEVERE, "Failed to build product " + product.name,
                        result.exit);
                  } else {
                    logger.log(
                        Level.WARNING, "Failed to build product {0} : {1}",
                        new Object[] {
                          product.name, JsonSink.stringify(result.result)
                        });
                  }
                }
              } finally {
                cleanWorkingDirectory(workDir);
              }
            } catch (IOException ex) {
              logger.log(
                  Level.SEVERE, "Failed to build product " + product.name, ex);
            }
            return passed;
          }
        });
        status.setBuildFuture(f);
      }
      return status.getBuildFuture();
    }
  }

  private Path createWorkingDirectory(String productName) throws IOException {
    Path path = os.getTempDir().resolve("prebake-" + productName);
    if (path.exists()) { cleanWorkingDirectory(path); }
    path.createDirectory(FilePerms.perms(0700, true));
    return path;
  }

  private void copyToWorkingDirectory(
      Iterable<Path> inputs, Path workingDir, Set<Path> workingDirInputs)
      throws IOException {
    Path root = files.getVersionRoot();
    for (Path input : inputs) {
      Path clientInput = root.resolve(input);
      // /client-dir/foo/bar.txt -> /tmp/working-dir/foo/bar.txt
      Path workingDirInput = workingDir.resolve(input);
      workingDirInputs.add(workingDirInput);
      mkdirs(workingDirInput.getParent());
      clientInput.copyTo(workingDirInput);
    }
  }

  private @Nonnull Executor.Output<Boolean> executeActions(
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
          if (outPaths.add(outPath)) { mkdirs(outPath); }
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

  private ImmutableList<Path> copyToRepo(
      String productName, Path workingDir, final Set<Path> workingDirInputs,
      ImmutableList<Glob> toCopyBack)
      throws IOException {
    ImmutableList<Path> outPaths = outputs(
        productName, workingDir, workingDirInputs, toCopyBack);
    ImmutableList<Path> existingPaths = sortedFilesMatching(toCopyBack);

    Set<Path> newPaths = Sets.newLinkedHashSet(outPaths);
    newPaths.removeAll(existingPaths);

    Set<Path> obsoletedPaths = Sets.newLinkedHashSet(existingPaths);
    obsoletedPaths.removeAll(outPaths);

    logger.log(
        Level.FINE, "{0} produced {1} file(s) : {2} new, {3} obsolete.",
        new Object[] {
          productName, outPaths.size(), newPaths.size(), obsoletedPaths.size()
        });

    final Path clientRoot = files.getVersionRoot();

    // Create directories for the new paths
    for (Path p : newPaths) { mkdirs(clientRoot.resolve(p).getParent()); }

    // Move the obsoleted files into the archive.
    if (!obsoletedPaths.isEmpty()) {
      Path archiveDir = clientRoot.resolve(FileNames.DIR)
          .resolve(FileNames.ARCHIVE);
      for (Path p : obsoletedPaths) {
        Path obsoleteDest = archiveDir.resolve(p);
        logger.log(Level.FINE, "Archived {0}", obsoleteDest);
        mkdirs(obsoleteDest.getParent());
        try {
          clientRoot.resolve(p).moveTo(obsoleteDest);
        } catch (IOException ex) {
          // Junk can accumulate under the archive dir.
          // Specifically, a directory could be archived, and then all attempts
          // to archive a regular file of the same name would file.
          LogRecord r = new LogRecord(Level.WARNING, "Failed to archive {0}");
          r.setParameters(new Object[] { obsoleteDest });
          r.setThrown(ex);
          logger.log(r);
        }
      }
      logger.log(
          Level.INFO, "{0} obsolete file(s) can be found under {1}",
          new Object[] { obsoletedPaths.size(), archiveDir });
    }

    ImmutableList.Builder<Path> outClientPaths = ImmutableList.builder();
    for (Path p : outPaths) {
      Path working = workingDir.resolve(p);
      Path client = clientRoot.resolve(p);
      working.moveTo(client, StandardCopyOption.REPLACE_EXISTING);
      outClientPaths.add(client);
    }

    return outClientPaths.build();
  }

  /**
   * Compute the list of files under the working directory that match a
   * product's output globs.
   */
  private ImmutableList<Path> outputs(
      String productName, final Path workingDir,
      final Set<Path> workingDirInputs, ImmutableList<Glob> toCopyBack)
      throws IOException {
    final GlobSet outputMatcher = new GlobSet();
    for (Glob outputGlob : toCopyBack) { outputMatcher.add(outputGlob); }
    // Get the prefix map so we only walk subtrees that are important.
    // E.g. for output globs
    //     [foo/lib/bar/*.lib, foo/lib/**.o, foo/lib/**.so, foo/bin/*.a]
    // this should yield the map
    //     "foo/lib" => [foo/lib/**.o, foo/lib/**.so]
    //     "foo/lib/bar" => [foo/lib/bar/*.lib]
    //     "foo/bin" => [foo/bin/*.a]
    // Note that the keys are sorted so that foo/lib always occurs before
    // foo/lib/bar so that the walker below does not do any unnecessary stating.
    final Map<String, List<Glob>> groupedByDir;
    {
      Multimap<String, Glob> byPrefix = outputMatcher.getGlobsGroupedByPrefix();
      groupedByDir = new TreeMap<String, List<Glob>>(new Comparator<String>() {
        // Sort so that shorter paths occur first.  That way we can start
        // walking the prefixes, and pick up the extra globs just in time when
        // we start walking those paths.
        public int compare(String a, String b) {
          long delta = ((long) a.length()) - b.length();
          return delta < 0 ? -1 : delta != 0 ? 1 : a.compareTo(b);
        }
      });
      String separator = files.getFileSystem().getSeparator();
      for (String prefix : byPrefix.keySet()) {
        if (!"/".equals(separator)) {  // Normalize / in glob to \ on Windows.
          prefix = prefix.replace("/", separator);
        }
        String pathPrefix = workingDir.resolve(prefix).toString();
        groupedByDir.put(
            pathPrefix, ImmutableList.copyOf(byPrefix.get(prefix)));
      }
    }
    class Walker {
      final ImmutableList.Builder<Path> out = ImmutableList.builder();
      final Set<String> walked = Sets.newHashSet();
      void walk(Path dir, GlobSet globs) throws IOException {
        // TODO: handle symbolic links
        String dirStr = dir.toString();
        List<Glob> extras = groupedByDir.get(dirStr);
        if (extras != null) {
          globs = new GlobSet(globs).addAll(extras);
          walked.add(dirStr);
        }
        for (Path p : dir.newDirectoryStream()) {
          BasicFileAttributes attrs = Attributes.readBasicFileAttributes(p);
          if (attrs.isRegularFile()) {
            Path relPath = workingDir.relativize(p);
            if (globs.matches(relPath)) { out.add(relPath); }
          } else if (attrs.isDirectory()) {
            walk(p, globs);
          }
        }
      }
    }
    Walker w = new Walker();
    for (Map.Entry<String, List<Glob>> e : groupedByDir.entrySet()) {
      String prefix = e.getKey();
      if (w.walked.contains(prefix)) { continue; }  // already walked
      Path p = workingDir.resolve(prefix);
      if (p.notExists()) {
        logger.log(
            Level.WARNING,
            "No dir {0} in output for product {1} with outputs {2}",
            new Object[] { p, productName, toCopyBack });
      } else {
        BasicFileAttributes atts = Attributes.readBasicFileAttributes(p);
        if (atts.isDirectory()) {
          w.walk(p, new GlobSet());
        }
      }
    }
    return w.out.build();
  }

  private void mkdirs(Path p) throws IOException {
    if (p.notExists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent); }
      p.createDirectory(FilePerms.perms(umask, true));
    }
  }

  private ImmutableList<Path> sortedFilesMatching(ImmutableList<Glob> globs) {
    List<Path> matching = Lists.newArrayList(files.matching(globs));
    // Sort inputs to remove a source of nondeterminism
    Collections.sort(matching);
    return ImmutableList.copyOf(matching);
  }

  private void cleanWorkingDirectory(Path workingDir) throws IOException {
    // Rename it and push a job onto the execer to recursively delete it.
    Path tmpName = null;
    for (int i = 0; i < 100; ++i) {
      // Likely to be on same physical partition.
      tmpName = workingDir.getParent().resolve("obsolete-" + i);
      if (tmpName.notExists()) { break; }
    }
    if (tmpName == null) {
      throw new IOException(
          "Failing to delete obsolete working dirs " + workingDir);
    }
    workingDir.moveTo(tmpName);
    final Path toDelete = tmpName;
    execer.submit(new Runnable() {
      public void run() {
        Files.walkFileTree(toDelete, new FileVisitor<Path>() {
          public FileVisitResult postVisitDirectory(Path dir, IOException ex) {
            if (ex != null) {
              logger.log(Level.WARNING, "Deleting " + dir, ex);
            }
            try {
              dir.deleteIfExists();
            } catch (IOException ioex) {
              logger.log(Level.WARNING, "Deleting " + dir, ioex);
            }
            return FileVisitResult.CONTINUE;
          }
          public FileVisitResult preVisitDirectory(Path dir) {
            return FileVisitResult.CONTINUE;
          }
          public FileVisitResult preVisitDirectoryFailed(
              Path dir, IOException ex) {
            logger.log(Level.WARNING, "Deleting " + dir, ex);
            return FileVisitResult.CONTINUE;
          }
          public FileVisitResult visitFile(Path f, BasicFileAttributes atts) {
            try {
              f.deleteIfExists();
            } catch (IOException ioex) {
              logger.log(Level.WARNING, "Deleting " + f, ioex);
            }
            return FileVisitResult.CONTINUE;
          }
          public FileVisitResult visitFileFailed(Path f, IOException ex) {
            logger.log(Level.WARNING, "Deleting " + f, ex);
            return FileVisitResult.CONTINUE;
          }
        });
      }
    });
  }

  // TODO: move listener results onto execer
  public final ArtifactListener<Product> prodListener
      = new ArtifactListener<Product>() {
    public void artifactDestroyed(String productName) {
      ProductStatus status = productStatuses.remove(productName);
      if (status != null) { status.setProduct(null); }
    }

    public void artifactChanged(Product product) {
      product = product.withoutNonBuildableInfo();
      ProductStatus status;
      {
        ProductStatus stub = new ProductStatus(product.name);
        status = productStatuses.putIfAbsent(product.name, stub);
        if (status == null) { status = stub; }
      }
      status.setProduct(product);
    }
  };

  public final ArtifactListener<ToolSignature> toolListener
      = new ArtifactListener<ToolSignature>() {
    public void artifactChanged(ToolSignature sig) {
      invalidate(sig.name);
    }
    public void artifactDestroyed(String toolName) { invalidate(toolName); }
    private void invalidate(String toolName) {
      for (ProductStatusChain statuses = toolDeps.get(toolName);
           statuses != null; statuses = statuses.next) {
        statuses.ps.setBuildFuture(null);
      }
    }
  };

  private final ArtifactListener<GlobUnion> fileListener
      = new ArtifactListener<GlobUnion>() {
    public void artifactChanged(GlobUnion union) { check(union.name); }
    public void artifactDestroyed(String productName) { check(productName); }
    private void check(String productName) {
      ProductStatus status = productStatuses.get(productName);
      if (status != null) { status.setBuildFuture(null); }
    }
  };

  @ParametersAreNonnullByDefault
  private final class ProductStatus implements NonFileArtifact {
    final String name;
    private Product product;
    /** Iff the product is built, non-null. */
    private Future<Boolean> buildFuture;
    private GlobUnion inputs;
    private ImmutableSet<String> tools;

    ProductStatus(String name) { this.name = name; }

    void setProduct(@Nullable Product newProduct) {
      if (newProduct != null) {
        newProduct = newProduct.withoutNonBuildableInfo();
      }
      synchronized (this) {
        if (product == null ? newProduct == null : product.equals(newProduct)) {
          return;
        }
        GlobUnion newInputs = newProduct != null
            ? new GlobUnion(newProduct.name, newProduct.inputs) : null;
        if (inputs != null ? !inputs.equals(newInputs) : newInputs != null) {
          if (inputs != null) { files.unwatch(inputs, fileListener); }
          inputs = newInputs;
          if (inputs != null) { files.watch(inputs, fileListener); }
        }
        product = newProduct;
        if (buildFuture != null) {
          buildFuture.cancel(true);
          buildFuture = null;
        }
        ImmutableSet<String> newTools;
        {
          ImmutableSet.Builder<String> b = ImmutableSet.builder();
          if (newProduct != null) {
            for (Action a : newProduct.actions) { b.add(a.toolName); }
          }
          newTools = b.build();
        }
        if (newTools != null ? !newTools.equals(tools) : tools != null) {
          if (tools != null) {
            for (String toolName : tools) {
              while (true) {
                ProductStatusChain statuses = toolDeps.get(toolName);
                if (statuses == null) { break; }
                ProductStatusChain newStatuses = statuses.without(this);
                if (newStatuses == statuses) { break; }
                if (newStatuses != null) {
                  if (toolDeps.replace(toolName, statuses, newStatuses)) {
                    break;
                  }
                } else if (toolDeps.remove(toolName, statuses)) {
                  break;
                }
              }
            }
          }
          tools = newTools;
          if (tools != null) {
            for (String toolName : tools) {
              while (true) {
                ProductStatusChain statuses = toolDeps.get(toolName);
                if (statuses == null) {
                  if (toolDeps.putIfAbsent(
                          toolName, new ProductStatusChain(this, null))
                      == null) {
                    break;
                  }
                } else if (toolDeps.replace(
                    toolName, statuses,
                    new ProductStatusChain(this, statuses))) {
                  break;
                }
              }
            }
          }
        }
      }
    }

    synchronized Product getProduct() { return product; }

    synchronized void setBuildFuture(@Nullable Future<Boolean> newBuildFuture) {
      if (buildFuture == newBuildFuture) { return; }
      if (buildFuture != null) {
        buildFuture.cancel(true);
      }
      buildFuture = newBuildFuture;
    }

    synchronized Future<Boolean> getBuildFuture() { return buildFuture; }

    public synchronized void markValid(boolean valid) {
      if (!valid) { setBuildFuture(null); }
    }
  }

  private static final class ProductStatusChain {
    final ProductStatus ps;
    final ProductStatusChain next;

    ProductStatusChain(ProductStatus ps, @Nullable ProductStatusChain next) {
      this.ps = ps;
      this.next = next;
    }

    ProductStatusChain without(ProductStatus toRemove) {
      if (ps == toRemove) { return next.without(toRemove); }
      if (next == null) { return this; }
      ProductStatusChain newNext = next.without(toRemove);
      return newNext == next ? this : new ProductStatusChain(ps, newNext);
    }
  }

  boolean unittestBackdoorProductStatus(String productName) {
    ProductStatus status = productStatuses.get(productName);
    if (status == null) { throw new IllegalArgumentException(productName); }
    Future<Boolean> bf;
    synchronized (status) { bf = status.buildFuture; }
    if (bf != null && bf.isDone()) {
      try {
        return bf.get().booleanValue();
      } catch (ExecutionException ex) {
        // Not done after all.
        Throwables.propagate(ex);
      } catch (InterruptedException ex) {
        // Not done after all.
        Throwables.propagate(ex);
      }
    }
    return false;
  }
}
