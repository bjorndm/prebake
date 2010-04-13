package org.prebake.service.bake;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.fs.GlobUnion;
import org.prebake.js.Executor;
import org.prebake.os.OperatingSystem;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

@ParametersAreNonnullByDefault
public final class Baker {
  private final OperatingSystem os;
  private final FileVersioner files;
  private final ConcurrentHashMap<String, ProductStatus> productStatuses
      = new ConcurrentHashMap<String, ProductStatus>();
  private final ScheduledExecutorService execer;
  private final ToolProvider toolbox;

  public Baker(
      OperatingSystem os, FileVersioner files,
      ToolProvider toolbox, ScheduledExecutorService execer) {
    this.os = os;
    this.files = files;
    this.toolbox = toolbox;
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

            ImmutableMap<String, FileAndHash> tools;
            Hash toolHashes;
            {
              ImmutableMap.Builder<String, FileAndHash> tb
                  = ImmutableMap.builder();
              Set<String> toolsUsed = Sets.newHashSet();
              Hash.Builder hb = Hash.builder();
              for (Action a : product.actions) {
                String toolName = a.toolName;
                if (!toolsUsed.add(toolName)) { continue; }
                FileAndHash tool = toolbox.getTool(toolName);
                tb.put(toolName, tool);
                Hash h = tool.getHash();
                if (h != null) { hb.withHash(h); }
              }
              tools = tb.build();
              toolHashes = hb.build();
            }


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

  private final ArtifactListener<Product> prodListener
      = new ArtifactListener<Product>() {
    public void artifactDestroyed(String productName) {
      ProductStatus status = productStatuses.remove(productName);
      if (status != null) {
        synchronized (status) {
          if (status.buildFuture != null) {
            status.buildFuture.cancel(true);
            status.buildFuture = null;
          }
        }
      }
    }

    public void artifactChanged(Product product) {
      product = product.withoutNonBuildableInfo();
      ProductStatus status;
      {
        ProductStatus stub = new ProductStatus(product.name);
        status = productStatuses.putIfAbsent(product.name, stub);
        if (status == null) { status = stub; }
      }
      synchronized (status) {
        if (status.product == null || !status.product.equals(product)) {
          if (status.buildFuture != null) {
            status.buildFuture.cancel(true);
            status.buildFuture = null;
          }
          status.product = product;
        }
      }
    }
  };

  private final ArtifactListener<ToolSignature> toolListener
      = new ArtifactListener<ToolSignature>() {
    public void artifactChanged(ToolSignature sig) {
      throw new Error("IMPLEMENT ME");  // TODO
    }

    public void artifactDestroyed(String toolName) {
      throw new Error("IMPLEMENT ME");  // TODO
    }
  };

  private final ArtifactListener<GlobUnion> fileListener
      = new ArtifactListener<GlobUnion>() {
    public void artifactChanged(GlobUnion union) {
      throw new Error("IMPLEMENT ME");  // TODO
    }

    public void artifactDestroyed(String productName) {
      throw new Error("IMPLEMENT ME");  // TODO
    }
  };
}
