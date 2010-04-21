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
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
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
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A service that services requests by the {@link org.prebake.client.Main}.
 *
 * @author mikesamuel@gmail.com
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
      + "|/\\.cvsignore$|/vssver.scc$"
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
   * @param portHint the port to use or -1 to let the system choose a port.
   * @param q receives commands from the outside.
   * @return the port opened
   * @throws IOException if a port could not be opened.  Always thrown if
   *     a port other than -1 was thrown and it could not be acquired.
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

  public synchronized void start(Runnable onClose) {
    if (this.onClose != null) { throw new IllegalStateException(); }
    if (onClose == null) {
      onClose = new Runnable() { public void run() { /* no op */} };
    }
    this.onClose = onClose;
    boolean setupSucceeded = false;
    try {
      try {
        setupChannel();
        setupFileSystemWatcher();
        setupCommandHandler();
      } catch (Throwable th) {
        // Handle any problems in case the shutdown hook causes VM exit.
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
            Thread.currentThread(), th);
      }
    } finally {
      if (!setupSucceeded) { close(); }
    }
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
      dir.getFileAttributeView(DosFileAttributeView.class).setHidden(true);
    }
    Path cmdlineFile = dir.resolve(fs.getPath(FileNames.CMD_LINE));
    Path portFile = dir.resolve(fs.getPath(FileNames.PORT));
    Path tokenFile = dir.resolve(fs.getPath(FileNames.TOKEN));
    if (!cmdlineFile.exists()) {
      cmdlineFile.createFile(FilePerms.perms(config.getUmask(), false));
    }
    write(cmdlineFile, CommandLineConfig.toArgv(config));
    int port = -1;
    if (portFile.exists()) {
      try {
        port = Integer.parseInt(read(portFile));
      } catch (NumberFormatException ex) {
        port = -1;  // Let openChannel choose a port.
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
        pattern += "|^" + Pattern.quote(clientRoot.toString()) + "(?:$|"
            + Pattern.quote(clientRoot.getFileSystem().getSeparator()) + ")";
        regex = Pattern.compile(pattern, base.flags());
      }
      public boolean apply(Path p) {
        return !regex.matcher(p.toString()).find();
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
        files.update(updates);
      }
    };
    pathConsumer.start();
  }

  private void setupCommandHandler() {
    commandConsumer = new Consumer<Commands>(cmdQueue) {
      @Override
      protected void consume(BlockingQueue<? extends Commands> q, Commands c) {
        boolean authed = false;
        final Appendable out = c.getResponse();
        // TODO: create a logger and thread it through to command execution so
        // that any problems that occur while running a command are reported
        // to the client as well as to the prebakery log.
        boolean closeChannel = true;
        try {
          for (Command cmd : c) {
            if (cmd.verb != Command.Verb.handshake && !authed) {
              logger.warning("Unauthorized command " + cmd);
              return;
            }
            switch (cmd.verb) {
              case build:
                try {
                  doBake(planner.getPlanGraph().makeRecipe(
                      ((Command.BuildCommand) cmd).products),
                      (Closeable) out);
                  closeChannel = false;
                } catch (PlanGraph.DependencyCycleException ex) {
                  logger.log(Level.WARNING, "{0}", ex.getMessage());
                }
                break;
              case files_changed:
                files.update(((Command.FilesChangedCommand) cmd).paths);
                break;
              case graph:
                DotRenderer.render(
                    planner.getPlanGraph(),
                    ((Command.GraphCommand) cmd).products, out);
                break;
              case handshake:
                authed = ((Command.HandshakeCommand) cmd).token.equals(token);
                break;
              case plan:
                try {
                  Recipe recipe = planner.getPlanGraph()
                      .makeRecipe(((Command.PlanCommand) cmd).products);
                  final StringBuilder sb = new StringBuilder();
                  final Appendable planOut = out;
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
                    out.append("Failed to update tool: ").append(ex.toString());
                  } catch (InterruptedException ex) {
                    out.append("Failed to update tool: ").append(ex.toString());
                  } catch (TimeoutException ex) {
                    out.append("Failed to update tool: ").append(ex.toString());
                  }
                  if (sig != null) { sigs.add(sig); }
                }
                for (ToolSignature sig : sigs) {
                  out.append(sig.name).append('\n');
                  Documentation doc = sig.help;
                  if (doc != null) {
                    out.append(indent(htmlToText(doc.summaryHtml)))
                        .append('\n');
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
          if (closeChannel) { Closeables.closeQuietly((Closeable) out); }
        }
      }
    };
    commandConsumer.start();
  }

  private void doBake(Recipe recipe, final Closeable outChannel) {
    recipe.cook(new Recipe.Chef() {
      List<String> ok = Collections.synchronizedList(
          Lists.<String>newArrayList());
      List<String> failed = Collections.synchronizedList(
          Lists.<String>newArrayList());

      public void cook(
          final Ingredient ingredient, final Function<Boolean, ?> whenDone) {
        execer.submit(new Runnable() {
          public void run() {
            String prod = ingredient.product;
            boolean status = false;
            try {
              status = baker.build(prod).get();
            } catch (ExecutionException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } catch (InterruptedException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } catch (RuntimeException ex) {
              logger.log(Level.SEVERE, "Failed to build " + prod, ex);
            } finally {
              (status ? ok : failed).add(prod);
              whenDone.apply(status);
            }
          }
        });
      }

      public void done(boolean allSucceeded) {
        logger.log(Level.FINE, "Built {0}", ok);
        if (!failed.isEmpty()) {
          logger.log(Level.WARNING, "Failed to build {0}", failed.size());
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

  private static String htmlToText(String html) {
    return html;  // TODO
  }

  private static String indent(String text) {
    return text.replaceAll("^|\r\n?|\n", "$0    ");
  }
}
