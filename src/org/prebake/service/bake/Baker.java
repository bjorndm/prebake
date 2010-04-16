package org.prebake.service.bake;

import org.prebake.core.ArtifactListener;
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
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

  public Baker(
      OperatingSystem os, FileVersioner files, Logger logger,
      ScheduledExecutorService execer) {
    this.os = os;
    this.files = files;
    this.logger = logger;
    this.execer = execer;
  }

  public void setToolBox(ToolProvider toolbox) { this.toolbox = toolbox; }

  public Future<Boolean> build(
      final String productName, final Function<Boolean, ?> whenBuilt) {
    assert toolbox != null;
    final ProductStatus status = productStatuses.get(productName);
    if (status == null) {
      logger.log(Level.WARNING, "Unrecognized product {0}", productName);
      ValueFuture<Boolean> vf = ValueFuture.create();
      vf.set(Boolean.FALSE);
      whenBuilt.apply(false);
      return vf;
    }
    synchronized (status) {
      if (status.getBuildFuture() == null) {
        final Product product = status.getProduct();
        Future<Boolean> f = execer.submit(new Callable<Boolean>() {
          public Boolean call() {
            Boolean result = build();
            whenBuilt.apply(result);
            return result;
          }

          private boolean build() {
            final Path workDir;
            final ImmutableList<Path> inputs;
            boolean passed = false;

            try {
              inputs = ImmutableList.copyOf(files.matching(product.inputs));
              System.err.println("inputs=" + inputs);  // TODO DEBUG
              workDir = createWorkingDirectory(product.name);
              try {
                final List<Path> paths = Lists.newArrayList();
                final Hash.Builder hashes = Hash.builder();

                Set<Path> workingDirInputs = Sets.newLinkedHashSet();
                copyToWorkingDirectory(inputs, workDir, workingDirInputs);
                Executor.Output<Boolean> result = executeActions(
                    workDir, product, inputs, paths, hashes);
                if (result != null && Boolean.TRUE.equals(result.result)) {
                  ImmutableList<Path> outputs;
                  outputs = copyToRepo(
                      workDir, workingDirInputs, product.outputs);
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
    int rootLen = root.getNameCount();
    for (Path input : inputs) {
      // /client-dir/foo/bar.txt -> /tmp/working-dir/foo/bar.txt
      Path workingDirInput = workingDir.resolve(
          input.subpath(rootLen, input.getNameCount()));
      workingDirInputs.add(workingDirInput);
      mkdirs(workingDirInput.getParent());
      input.copyTo(workingDirInput);
    }
  }

  private Executor.Output<Boolean> executeActions(
      Path workingDir, Product p, List<Path> inputs,
      final List<Path> paths, final Hash.Builder hashes)
      throws IOException {
    List<String> inputStrs = Lists.newArrayList();
    for (Path input : inputs) { inputStrs.add(input.toString()); }
    Collections.sort(inputStrs);
    ImmutableMap.Builder<String, Object> actuals = ImmutableMap.builder();
    StringBuilder productJs = new StringBuilder();
    JsonSink productJsSink = new JsonSink(productJs);
    Map<String, String> toolNameToLocalName = Maps.newHashMap();
    // First, load each tool module.
    for (Action a : p.actions) {
      String toolName = a.toolName;
      if (toolNameToLocalName.containsKey(toolName)) { continue; }
      String localName = "tool_" + toolNameToLocalName.size();
      toolNameToLocalName.put(toolName, localName);
      FileAndHash tool = toolbox.getTool(toolName);
      if (tool.getHash() != null) {
        paths.add(tool.getPath());
        hashes.withHash(tool.getHash());
      }
      actuals.put(localName, tool.getContentAsString(Charsets.UTF_8));
    }
    // Second, store the product.
    productJsSink.write("var product = Object.frozenCopy(")
        .writeValue(p)
        .write(");\n");
    // Third, for each action, invoke its tool's run method with the proper
    // arguments.
    for (Action a : p.actions) {
      productJsSink.write(
          toolNameToLocalName.get(a.toolName))
          .write("({load: load})")
          .write(".run(")
          .writeValue(a.options).write(", ")
          // TODO: define exec based on os
          // TODO: fetch the inputs based on the action's input globs
          // and
          .writeValue(inputStrs).write(", product, exec);\n");
    }
    productJsSink.writeValue(true);
    try {
      Executor exec = Executor.Factory.createJsExecutor(
          Executor.Input.builder(productJs.toString(), "product-" + p.name)
              .withBase(workingDir)
              .build());

      return exec.run(Boolean.class, logger, new Loader() {
        public Executor.Input load(Path p) throws IOException {
          FileAndHash fh = files.load(p);
          if (fh.getHash() != null) {
            paths.add(fh.getPath());
            hashes.withHash(fh.getHash());
          }
          return Executor.Input.builder(
              fh.getContentAsString(Charsets.UTF_8), p).build();
        }
      });
    } catch (Executor.AbnormalExitException ex) {
      logger.log(Level.SEVERE, "Failed to build product " + p.name, ex);
      return null;
    } catch (Executor.MalformedSourceException ex) {
      logger.log(Level.SEVERE, "Failed to build product " + p.name, ex);
      return null;
    }
  }

  private ImmutableList<Path> copyToRepo(
      Path workingDir, final Set<Path> workingDirInputs, List<Glob> toCopyBack)
      throws IOException {
    final GlobSet outputMatcher = new GlobSet();
    for (Glob outputGlob : toCopyBack) { outputMatcher.add(outputGlob); }
    final Path clientRoot = files.getVersionRoot();
    final ImmutableList.Builder<Path> relPathsUpdated = ImmutableList.builder();
    try {
      final int workingDirCount = workingDir.getNameCount();
      Files.walkFileTree(workingDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
          if (attrs.isRegularFile() && !workingDirInputs.contains(p)) {
            Path relPath = p.subpath(workingDirCount, p.getNameCount());
            if (outputMatcher.matches(relPath)) {
              relPathsUpdated.add(relPath);
              Path clientPath = clientRoot.resolve(relPath);
              try {
                p.copyTo(
                    clientPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
                // TODO: apply umask
              } catch (IOException ex) {
                throw new IOError(ex);
              }
            }
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOError err) {
      throw (IOException) err.getCause();
    }
    return relPathsUpdated.build();
  }

  private void mkdirs(Path p) throws IOException {
    if (p.notExists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent); }
      p.createDirectory(FilePerms.perms(0700, true));
    }
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
    public void artifactChanged(ToolSignature sig) { invalidate(sig.name); }
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
}
