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

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.fs.FileVersioner;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.FilePerms;
import org.prebake.fs.GlobUnion;
import org.prebake.fs.NonFileArtifact;
import org.prebake.js.Executor;
import org.prebake.js.JsonSink;
import org.prebake.os.OperatingSystem;
import org.prebake.service.ArtifactDescriptors;
import org.prebake.service.LogHydra;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ValueFuture;

/**
 * Maintains a set of products and whether they're up to date.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Baker {
  private final OperatingSystem os;
  private final FileVersioner files;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final Logger logger;
  private final LogHydra logHydra;
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
  private Oven oven;
  private Finisher finisher;

  /**
   * @param os used to kick off processes when executing {@link Action action}s.
   * @param files versions the client directory.
   * @param umask for all files and directories created by the baker.
   * @param logger receives messages about {@link Product product} statuses and
   *     from plan files, tool files, and external processes.
   * @param execer an executor which is used to schedule periodic maintenance
   *     tasks and which is used to update product definitions.
   */
  public Baker(
      OperatingSystem os, FileVersioner files,
      ImmutableMap<String, ?> commonJsEnv, int umask, Logger logger,
      LogHydra logHydra, ScheduledExecutorService execer) {
    this.os = os;
    this.files = files;
    this.commonJsEnv = commonJsEnv;
    this.umask = umask;
    this.logger = logger;
    this.logHydra = logHydra;
    this.execer = execer;
  }

  /**
   * Must be called exactly once before {@link #bake} to arrange the set of
   * tools available to plan files.
   */
  public void setToolBox(ToolProvider toolbox) {
    if (toolbox == null) { throw new IllegalArgumentException(); }
    if (this.toolbox != null) { throw new IllegalStateException(); }
    this.toolbox = toolbox;
    this.oven = new Oven(os, files, commonJsEnv, toolbox, logger);
    this.finisher = new Finisher(files, umask, logger);
  }

  /**
   * Returns a promise to bring the named product up-to-date.
   * @return a future whose result is true if the product is up-to-date, and
   *     false if it cannot be brought up-to-date.
   */
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
            String artifactDescriptor = ArtifactDescriptors.forProduct(
                product.name);
            try {
              logHydra.artifactProcessingStarted(
                  artifactDescriptor,
                  EnumSet.of(LogHydra.DataSource.INHERITED_FILE_DESCRIPTORS));
            } catch (IOException ex) {
              logger.log(Level.SEVERE, "Failed to open log file", ex);
            }
            try {
              return build();
            } finally {
              logHydra.artifactProcessingEnded(artifactDescriptor);
            }
          }

          private boolean build() {
            logger.log(Level.INFO, "Starting bake of product {0}", productName);
            final Path workDir;
            final ImmutableList<Path> inputs;
            boolean passed = false;

            try {
              inputs = sortedFilesMatching(files, product.inputs);
              workDir = createWorkingDirectory(product.name);
              try {
                ImmutableList.Builder<Path> paths = ImmutableList.builder();
                Hash.Builder hashes = Hash.builder();

                Set<Path> workingDirInputs = Sets.newLinkedHashSet();
                copyToWorkingDirectory(inputs, workDir, workingDirInputs);
                Executor.Output<Boolean> result = oven.executeActions(
                    workDir, product, inputs, paths, hashes);
                if (Boolean.TRUE.equals(result.result)) {
                  ImmutableList<Path> outputs;
                  // TODO: can't pass if there are problems moving files to the
                  // repo.
                  outputs = finisher.moveToRepo(
                      product.name, workDir, workingDirInputs, product.outputs);
                  files.updateFiles(outputs);
                  synchronized (status) {
                    if (status.product.equals(product)
                        && files.updateArtifact(
                            addresser, status, paths.build(), hashes.build())) {
                      passed = true;
                      logger.log(
                          Level.INFO, "Product up to date: {0}", product.name);
                    } else {
                      logger.log(
                          Level.WARNING, "Version skew for {0}", product.name);
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

  public Set<String> getUpToDateProducts() {
    ImmutableSet.Builder<String> upToDate = ImmutableSet.builder();
    for (ProductStatus status : ImmutableSet.copyOf(productStatuses.values())) {
      if (status.isUpToDate()) { upToDate.add(status.name); }
    }
    return upToDate.build();
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

  private void mkdirs(Path p) throws IOException { mkdirs(p, umask); }

  static void mkdirs(Path p, int umask) throws IOException {
    if (p.notExists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent, umask); }
      p.createDirectory(FilePerms.perms(umask, true));
    }
  }

  static ImmutableList<Path> sortedFilesMatching(
      FileVersioner files, ImmutableList<Glob> globs) {
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
  /**
   * Should be linked to the {@link org.prebake.service.plan.Planner} to receive
   * updates when product definitions change.
   */
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

  /**
   * Should be linked to the {@link ToolProvider} to receive updates when tool
   * definitions change.
   */
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
    private boolean upToDate;

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
      this.upToDate = valid;
    }

    synchronized boolean isUpToDate() { return upToDate; }
  }

  private static final class ProductStatusChain {
    final ProductStatus ps;
    final ProductStatusChain next;

    ProductStatusChain(ProductStatus ps, @Nullable ProductStatusChain next) {
      this.ps = ps;
      this.next = next;
    }

    ProductStatusChain without(ProductStatus toRemove) {
      if (ps == toRemove) {
        return next != null ? next.without(toRemove) : null;
      }
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
