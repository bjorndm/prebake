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

package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.channel.Commands;
import org.prebake.channel.FileNames;
import org.prebake.core.Documentation;
import org.prebake.fs.DbFileVersioner;
import org.prebake.fs.DirectoryHooks;
import org.prebake.fs.FilePerms;
import org.prebake.fs.FileVersioner;
import org.prebake.os.OperatingSystem;
import org.prebake.service.bake.Baker;
import org.prebake.service.plan.Ingredient;
import org.prebake.service.plan.PlanGraph;
import org.prebake.service.plan.Planner;
import org.prebake.service.plan.Recipe;
import org.prebake.service.tools.ToolBox;
import org.prebake.service.tools.ToolSignature;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.sleepycat.je.Environment;

import java.io.Closeable;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A service that services requests by the {@link org.prebake.client.Main}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public abstract class Prebakery implements Closeable {
  private final Config config;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final String token;
  private final LinkedBlockingQueue<Commands> cmdQueue;
  private final Logger logger;
  private final ScheduledExecutorService execer;
  private final OperatingSystem os;
  private Environment env;
  private FileVersioner files;
  private Runnable onClose;
  private Predicate<Path> toWatch;
  private Consumer<Path> pathConsumer;
  private Consumer<Commands> commandConsumer;
  private ToolBox tools;
  private Planner planner;
  private Baker baker;

  /**
   * @see
   *   <a href="http://code.google.com/p/prebake/wiki/IgnoredFileSet">wiki</a>
   */
  private static final Pattern DEFAULT_IGNORE_PATTERN = Pattern.compile(
      ""
      + "\\.(?:pyc|elc|bak|rej|prej|tmp)$"
      + "|~$"
      + "|/#[^/]*#$"
      + "|/%[^/]*%$"
      + "|/\\.(?:#+|_+)$"
      + "|/(?:CVS|SCCS|\\.svn)(?:/|$)"
      + "|/\\.cvsignore$|\\.gitignore|/vssver.scc$"
      + "|/\\.DS_Store$",
      Pattern.DOTALL);

  public Prebakery(
      Config config, ImmutableMap<String, ?> commonJsEnv,
      ScheduledExecutorService execer, OperatingSystem os, Logger logger) {
    assert config != null;
    config = staticCopy(config);
    this.config = config;
    this.commonJsEnv = commonJsEnv;
    this.execer = execer;
    this.os = os;
    this.logger = logger;
    this.token = makeToken();
    this.cmdQueue = new LinkedBlockingQueue<Commands>(4);
  }

  public synchronized void close() {
    try {
      // Close input channels first.
      if (commandConsumer != null) {
        commandConsumer.close();
        commandConsumer = null;
      }
      if (pathConsumer != null) {
        pathConsumer.close();
        pathConsumer = null;
      }
      // Close stores next
      if (planner != null) {
        planner.close();
        planner = null;
      }
      if (tools != null) {
        Closeables.closeQuietly(tools);
        tools = null;
      }
      if (files != null) {
        files.close();
        files = null;
      }
      if (os instanceof Closeable) {
        Closeables.closeQuietly((Closeable) os);
      }
      if (!execer.isShutdown()) { execer.shutdown(); }
      // Close the DB environment after DB users.
      if (env != null) {
        env.close();
        env = null;
      }
      // Make a best effort to delete the token and port but don't abort.
      try {
        config.getClientRoot().resolve(FileNames.DIR).resolve(FileNames.TOKEN)
            .deleteIfExists();
      } catch (IOException ex) {
        // OK
      }
      try {
        config.getClientRoot().resolve(FileNames.DIR).resolve(FileNames.PORT)
            .deleteIfExists();
      } catch (IOException ex) {
        // OK
      }
    } finally {
      Runnable onClose = this.onClose;
      this.onClose = null;
      if (onClose != null) { onClose.run(); }
    }
  }

  public Config getConfig() { return config; }

  /**
   * @param portHint the port to use or 0 to let the system choose a port.
   * @param q receives commands from the outside.
   * @return the port opened
   * @throws IOException if a port could not be opened.  Always thrown if
   *     a port other than 0 was specified and it could not be acquired.
   */
  protected abstract int openChannel(int portHint, BlockingQueue<Commands> q)
      throws IOException;

  /**
   * Generates a string that clients have to echo back to demonstrate that they
   * can reach the portion of the file system modifiable by the service.
   * In non test environments should return an unguessable string.
   */
  protected abstract @Nonnull String makeToken();

  /**
   * Creates a database environment rooted at the given directory.
   * Test environments may create the database elsewhere.
   */
  protected abstract @Nonnull Environment createDbEnv(Path dir)
      throws IOException;

  public synchronized void start(@Nullable Runnable onClose) {
    if (this.onClose != null) { throw new IllegalStateException(); }
    logger.log(Level.INFO, "Starting");
    if (onClose == null) { onClose = new Noop(); }
    this.onClose = onClose;
    boolean setupSucceeded = false;
    try {
      try {
        setupChannel();
        logger.log(Level.INFO, "Set up channel");
        setupFileSystemWatcher();
        logger.log(Level.INFO, "Set up file system watcher");
        setupCommandHandler();
        logger.log(Level.INFO, "Set up command handler");
        setupSucceeded = true;
      } catch (Throwable th) {
        UncaughtExceptionHandler handler
            = Thread.getDefaultUncaughtExceptionHandler();
        if (handler != null) {
          // Handle any problems in case the shutdown hook causes VM exit.
          handler.uncaughtException(Thread.currentThread(), th);
        }
        logger.log(Level.WARNING, "Exception during start", th);
      }
    } finally {
      if (!setupSucceeded) { close(); }
    }
    logger.log(Level.INFO, "Started");
  }

  /**
   * Connects the command queue to the outside world and sets up a directory
   * containing the information that clients need to connect.
   */
  private void setupChannel() throws IOException {
    Path clientRoot = config.getClientRoot();
    FileSystem fs = clientRoot.getFileSystem();
    Path dir = clientRoot.resolve(fs.getPath(FileNames.DIR));
    if (!dir.exists()) {
      dir.createDirectory(FilePerms.perms(config.getUmask(), true));
      if ("/".equals(fs.getSeparator())) {
        DosFileAttributeView dosAttrs = dir.getFileAttributeView(
            DosFileAttributeView.class);
        if (dosAttrs != null) { dosAttrs.setHidden(true); }
      }
    }
    Path cmdlineFile = dir.resolve(fs.getPath(FileNames.CMD_LINE));
    Path portFile = dir.resolve(fs.getPath(FileNames.PORT));
    Path tokenFile = dir.resolve(fs.getPath(FileNames.TOKEN));
    if (!cmdlineFile.exists()) {
      cmdlineFile.createFile(FilePerms.perms(config.getUmask(), false));
    }
    write(cmdlineFile, CommandLineConfig.toArgv(config));
    int port = 0;
    if (portFile.exists()) {
      try {
        port = Integer.parseInt(read(portFile));
      } catch (NumberFormatException ex) {
        port = 0;  // Let openChannel choose a port.
      }
    } else {
      portFile.createFile(FilePerms.perms(config.getUmask(), false));
    }
    port = openChannel(port, cmdQueue);
    write(portFile, "" + port);
    if (!tokenFile.exists()) {
      tokenFile.createFile(FilePerms.perms(config.getUmask(), false));
    }
    write(tokenFile, token);

    this.toWatch = new Predicate<Path>() {
      final Pattern regex;
      {
        // Make sure the clientRoot and everything under it is ignored.
        Pattern base = config.getIgnorePattern() != null
            ? config.getIgnorePattern() : DEFAULT_IGNORE_PATTERN;
        String pattern = base.pattern();
        Path clientRoot = config.getClientRoot();
        pattern += "|^" + Pattern.quote(clientRoot.toString()) + "(?:"
            + Pattern.quote(clientRoot.getFileSystem().getSeparator()) + ")?$";
        regex = Pattern.compile(pattern, base.flags());
      }
      public boolean apply(Path p) {
        String pathStr = p.toString();
        String sep = p.getFileSystem().getSeparator();
        if (!"/".equals(sep)) { pathStr = pathStr.replace(sep, "/"); }
        return !regex.matcher(pathStr).find();
      }
    };

    this.env = createDbEnv(dir);
    this.files = new DbFileVersioner(env, clientRoot, toWatch, logger);
    this.baker = new Baker(
        os, files, commonJsEnv, config.getUmask(), logger, execer);
    this.tools = new ToolBox(
        files, commonJsEnv, config.getToolDirs(), logger, baker.toolListener,
        execer);
    this.baker.setToolBox(this.tools);
    this.planner = new Planner(
        files, commonJsEnv, tools, config.getPlanFiles(), logger,
        this.baker.prodListener, execer);
  }

  private void setupFileSystemWatcher() {
    DirectoryHooks hooks = new DirectoryHooks(config.getClientRoot(), toWatch);
    pathConsumer = new Consumer<Path>(hooks.getUpdates()) {
      @Override
      protected void consume(BlockingQueue<? extends Path> q, Path x) {
        List<Path> updates = Lists.newArrayList();
        updates.add(x);
        q.drainTo(updates, 64);
        files.updateFiles(updates);
      }
    };
    pathConsumer.start();
    try {
      hooks.start();
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to start directory hooks", ex);
    }
  }

  private void setupCommandHandler() {
    commandConsumer = new Consumer<Commands>(cmdQueue) {
      @Override
      protected void consume(BlockingQueue<? extends Commands> q, Commands c) {
        boolean authed = false;
        final OutputStream out = c.getResponse();
        final Writer w = new OutputStreamWriter(out, Charsets.UTF_8);
        boolean closeChannel = true;
        ClientChannel ccl = null;
        try {
          for (Command cmd : c) {
            if (cmd.verb != Command.Verb.handshake && !authed) {
              logger.warning("Unauthorized command " + cmd);
              return;
            }
            switch (cmd.verb) {
              case bake:
                ccl = new ClientChannel(w);
                logger.addHandler(ccl);
                try {
                  planner.getProducts();
                  Set<String> products = ((Command.BakeCommand) cmd).products;
                  doBake(
                      products, planner.getPlanGraph().makeRecipe(products),
                      ccl);
                  // Closing the channel, and unregistering the log handler is
                  // now the recipe callback's responsibility
                  closeChannel = false;
                  ccl = null;
                } catch (PlanGraph.DependencyCycleException ex) {
                  logger.log(Level.WARNING, "{0}", ex.getMessage());
                } catch (PlanGraph.MissingProductsException ex) {
                  logger.log(Level.WARNING, "{0}", ex.getMessage());
                }
                break;
              case files_changed:
                files.updateFiles(((Command.FilesChangedCommand) cmd).paths);
                break;
              case graph:
                DotRenderer.render(
                    planner.getPlanGraph(),
                    ((Command.GraphCommand) cmd).products, w);
                break;
              case handshake:
                authed = ((Command.HandshakeCommand) cmd).token.equals(token);
                break;
              case plan:
                ccl = new ClientChannel(w);
                logger.addHandler(ccl);
                try {
                  planner.getProducts();
                  Recipe recipe = planner.getPlanGraph()
                      .makeRecipe(((Command.PlanCommand) cmd).products);
                  final StringBuilder sb = new StringBuilder();
                  final Appendable planOut = w;
                  recipe.cook(new Recipe.Chef() {
                    public void cook(
                        Ingredient ingredient, Function<Boolean, ?> whenDone) {
                      sb.append(ingredient.product).append('\n');
                      whenDone.apply(true);
                    }
                    public void done(boolean allSucceeded) {
                      assert allSucceeded;
                      try {
                        planOut.append(sb.toString());
                      } catch (IOException ex) {
                        throw new IOError(ex);
                      }
                    }
                  });
                } catch (PlanGraph.DependencyCycleException ex) {
                  logger.log(Level.WARNING, "{0}", ex.getMessage());
                } catch (PlanGraph.MissingProductsException ex) {
                  logger.log(Level.WARNING, "{0}", ex.getMessage());
                }
                break;
              case shutdown: Prebakery.this.close(); break;
              case sync:
                try {
                  pathConsumer.waitUntilEmpty();
                } catch (InterruptedException ex) {
                  logger.log(
                      Level.WARNING, "Sync command interrupted {0}", cmd);
                  return;
                }
                break;
              case tool_help:
                List<ToolSignature> sigs = Lists.newArrayList();
                for (Future<ToolSignature> f
                     : tools.getAvailableToolSignatures()) {
                  ToolSignature sig = null;
                  try {
                    sig = f.get(5, TimeUnit.SECONDS);
                  } catch (ExecutionException ex) {
                    w.append("Failed to update tool: ").append(ex.toString());
                  } catch (InterruptedException ex) {
                    w.append("Failed to update tool: ").append(ex.toString());
                  } catch (TimeoutException ex) {
                    w.append("Failed to update tool: ").append(ex.toString());
                  }
                  if (sig != null) { sigs.add(sig); }
                }
                for (ToolSignature sig : sigs) {
                  w.append(sig.name).append('\n');
                  Documentation doc = sig.help;
                  if (doc != null) {
                    w.append(indent(doc.summaryHtml.plainText())).append('\n');
                  }
                }
                break;
            }
          }
        } catch (IOError ex) {
          logger.log(
              Level.INFO, "Failed to service command " + c, ex.getCause());
        } catch (IOException ex) {  // Client disconnect?
          logger.log(Level.INFO, "Failed to service command " + c, ex);
        } finally {
          if (ccl != null) {
            Closeables.closeQuietly(ccl);
            logger.removeHandler(ccl);
          }
          if (closeChannel) {
            try {
              w.flush();
              out.write((byte) 0);
              w.close();
            } catch (IOException ex) {
              logger.log(Level.WARNING, "Failed to close client stream", ex);
            }
          }
        }
      }
    };
    commandConsumer.start();
  }

  private void doBake(
      final Set<String> products, Recipe recipe,
      final ClientChannel outChannel) {
    recipe.cook(new Recipe.Chef() {
      List<String> ok = Collections.synchronizedList(
          Lists.<String>newArrayList());
      List<String> failed = Collections.synchronizedList(
          Lists.<String>newArrayList());

      public void cook(
          final Ingredient ingredient, final Function<Boolean, ?> whenDone) {
        execer.submit(new Runnable() {
          public void run() {
            logger.log(Level.INFO, "Cooking {0}", ingredient.product);
            String prod = ingredient.product;
            boolean status = false;
            try {
              status = baker.bake(prod).get();
            } catch (ExecutionException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } catch (InterruptedException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } catch (RuntimeException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } finally {
              (status ? ok : failed).add(prod);
              if (status) {
                logger.log(Level.INFO, "Cooked {0}", ingredient.product);
              } else {
                logger.log(
                    Level.WARNING, "Failed to cook {0}", ingredient.product);
              }
              whenDone.apply(status);
            }
          }
        });
      }

      public void done(boolean allSucceeded) {
        logger.log(
            Level.FINE, "Bake of {0} {1}",
            new Object[] { products, allSucceeded ? "succeeded" : "failed" });
        if (!failed.isEmpty()) {
          logger.log(Level.WARNING, "Failed to build {0}", failed.size());
        }
        logger.removeHandler(outChannel);
        try {
          outChannel.flush();
          outChannel.out.append('\0');
        } catch (IOException ex) {
          logger.log(Level.INFO, "Failed to close channel", ex);
        }
        Closeables.closeQuietly(outChannel);
      }
    });
  }

  /**
   * A {@code Config} snapshot that dodges problems that might arise from
   * mutable {@code Config}s.
   */
  private static Config staticCopy(Config config) {
    final Path clientRoot = config.getClientRoot();
    final Pattern ignorePattern = config.getIgnorePattern();
    final String pathSep = config.getPathSeparator();
    final Set<Path> planFiles = ImmutableSet.copyOf(config.getPlanFiles());
    final List<Path> toolDirs = ImmutableList.copyOf(config.getToolDirs());
    final int umask = config.getUmask();
    return new Config() {
      public Path getClientRoot() { return clientRoot; }
      public Pattern getIgnorePattern() { return ignorePattern; }
      public String getPathSeparator() { return pathSep; }
      public Set<Path> getPlanFiles() { return planFiles; }
      public List<Path> getToolDirs() { return toolDirs; }
      public int getUmask() { return umask; }
    };
  }

  private static void write(Path p, String content) throws IOException {
    Writer w = new OutputStreamWriter(
        p.newOutputStream(
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE),
        Charsets.UTF_8);
    try {
      w.write(content);
    } finally {
      w.close();
    }
  }

  private static String read(Path p) throws IOException {
    Reader r = new InputStreamReader(
        p.newInputStream(StandardOpenOption.READ), Charsets.UTF_8);
    try {
      return CharStreams.toString(r);
    } finally {
      r.close();
    }
  }

  private static String indent(String text) {
    return text.replaceAll("^|\r\n?|\n", "$0    ");
  }

  private static final class Noop implements Runnable {
    public void run() { /* no op */}
  }
}
