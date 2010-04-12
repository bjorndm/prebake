package org.prebake.service.bake;

import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.plan.ProductWatcher;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

@ParametersAreNonnullByDefault
public final class Baker implements ProductWatcher {
  private final OperatingSystem os;
  private final FileVersioner files;
  private final ConcurrentHashMap<String, ProductStatus> productStatuses
      = new ConcurrentHashMap<String, ProductStatus>();
  private final ScheduledExecutorService execer;

  public Baker(
      OperatingSystem os, FileVersioner files,
      ScheduledExecutorService execer) {
    this.os = os;
    this.files = files;
    this.execer = execer;
  }

  public Future<Hash> build(
      final String productName, final Function<Boolean, ?> whenBuilt) {
    final ProductStatus status = productStatuses.get(productName);
    if (status == null) { return null; }
    synchronized (status) {
      if (status.buildFuture == null) {
        Future<Hash> f = execer.submit(new Callable<Hash>() {
          final Product product = status.product;
          public Hash call() throws Exception {
            final Path workDir;
            final ImmutableList<Path> inputs;
            final ImmutableList<Path> outputs;
            final ImmutableList.Builder<Hash> inputHashes
                = ImmutableList.builder();

            inputs = ImmutableList.copyOf(files.matching(product.inputs));
            workDir = createWorkingDirectory();
            copyToWorkingDirectory(inputs, workDir, inputHashes);
            Executor.Output<Object> toolResult = executeActions(
                product.actions, inputs);
            outputs = copyToRepo(workDir, product.outputs);
            cleanWorkingDirectory(workDir);
            // files.update(outputs);  // TODO

            throw new Error("IMPLEMENT ME");  // TODO
          }
        });
        status.buildFuture = f;
      }
      return status.buildFuture;
    }
  }

  private Path createWorkingDirectory() {
    throw new Error("IMPLEMENT ME");  // TODO
  }

  private void copyToWorkingDirectory(
      Iterable<Path> files, Path workingDir,
      ImmutableList.Builder<? super Hash> hashesOut) {
    throw new Error("IMPLEMENT ME");  // TODO
  }

  private Executor.Output<Object> executeActions(
      Iterable<Action> actions, List<Path> inputs) {
    throw new Error("IMPLEMENT ME");  // TODO
  }

  private ImmutableList<Path> copyToRepo(
      Path workingDir, List<Glob> toCopyBack) {
    throw new Error("IMPLEMENT ME");  // TODO
  }

  private void cleanWorkingDirectory(Path workingDir) {
    throw new Error("IMPLEMENT ME");  // TODO
  }

  public void productDestroyed(String product) {
    ProductStatus status = productStatuses.remove(product);
    if (status != null) {
      synchronized (status) {
        if (status.buildFuture != null) {
          status.buildFuture.cancel(true);
          status.buildFuture = null;
        }
      }
    }
  }

  public void productDefined(Product product) {
    product = product.withoutNonBuildableInfo();
    ProductStatus status;
    {
      ProductStatus stub = new ProductStatus(product.name);
      status = productStatuses.putIfAbsent(product.name, stub);
      if (status == null) { status = stub; }
    }
    synchronized (status) {
      if (status.product == null || !status.product.equals(product)) {
        status.productHash = null;
        status.toolHashes = null;
        status.inputHashes = null;
        if (status.buildFuture != null) {
          status.buildFuture.cancel(true);
          status.buildFuture = null;
        }
        status.product = product;
      }
    }
  }
}
