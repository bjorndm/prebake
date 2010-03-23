package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.channel.Commands;
import org.prebake.channel.FileNames;
import org.prebake.fs.DirectoryHooks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.sleepycat.je.Environment;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A service that services requests by the {@link org.prebake.client.Main}.
 *
 * @author mikesamuel@gmail.com
 */
public abstract class Prebakery implements Closeable {
  private final Config config;
  private final String token;
  private final LinkedBlockingQueue<Commands> cmdQueue;
  private final Logger logger;
  private Environment env;
  private FileHashes fileHashes;
  private Runnable onClose;
  private Consumer<Path> pathConsumer;
  private Consumer<Commands> commandConsumer;

  public Prebakery(Config config, Logger logger) {
    assert config != null;
    config = staticCopy(config);
    this.logger = logger;
    this.config = config;
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
      if (fileHashes != null) {
        fileHashes.close();
        fileHashes = null;
      }
      // Close the DB environment after DB users.
      if (env != null) {
        env.close();
        env = null;
      }
    } finally {
      Runnable onClose = this.onClose;
      this.onClose = null;
      if (onClose != null) { onClose.run(); }
    }
  }

  public Config getConfig() { return config; }

  protected abstract int openChannel(int portHint, BlockingQueue<Commands> q)
      throws IOException;

  protected abstract String makeToken();

  protected abstract Environment createDbEnv(Path dir) throws IOException;

  public synchronized void start(Runnable onClose) {
    if (this.onClose != null) { throw new IllegalStateException(); }
    if (onClose == null) { onClose = new Runnable() { public void run() {} }; }
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
      dir.createDirectory();  // TODO umask
    }
    Path cmdlineFile = dir.resolve(fs.getPath(FileNames.CMD_LINE));
    Path portFile = dir.resolve(fs.getPath(FileNames.PORT));
    Path tokenFile = dir.resolve(fs.getPath(FileNames.TOKEN));
    if (!cmdlineFile.exists()) {
      cmdlineFile.createFile();  // TODO umask
    }
    write(cmdlineFile, CommandLineConfig.toArgv(config));
    int port = -1;
    if (portFile.exists()) {
      try {
        port = Integer.parseInt(read(portFile));
      } catch (NumberFormatException ex) {
        port = -1;
      }
    }
    port = openChannel(port, cmdQueue);
    write(portFile, "" + port);
    if (!tokenFile.exists()) {
      tokenFile.createFile();  // TODO umask and delete on exit
    }
    write(tokenFile, token);

    this.env = createDbEnv(dir);
    this.fileHashes = new FileHashes(env, clientRoot);
  }

  private void setupFileSystemWatcher() {
    DirectoryHooks hooks = new DirectoryHooks(config.getClientRoot());
    pathConsumer = new Consumer<Path>(hooks.getUpdates()) {
      @Override
      protected void consume(BlockingQueue<? extends Path> q, Path x) {
        List<Path> updates = Lists.newArrayList();
        updates.add(x);
        q.drainTo(updates, 64);
        fileHashes.update(updates);
      }
    };
    pathConsumer.start();
  }

  private void setupCommandHandler() {
    commandConsumer = new Consumer<Commands>(cmdQueue) {
      @Override
      protected void consume(BlockingQueue<? extends Commands> q, Commands c) {
        boolean authed = false;
        for (Command cmd : c) {
          if (cmd.verb != Command.Verb.handshake && !authed) {
            logger.warning("Unauthorized command " + cmd);
            return;
          }
          switch (cmd.verb) {
            case handshake:
              authed = ((Command.HandshakeCommand) cmd).token.equals(token);
              continue;
            case shutdown: Prebakery.this.close(); break;
            case sync:
              try {
                pathConsumer.waitUntilEmpty();
              } catch (InterruptedException ex) {
                logger.warning("Sync command interrupted " + cmd);
                return;
              }
              break;
          }
        }
      }
    };
    commandConsumer.start();
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
    OutputStream out = p.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
    try {
      Writer w = new OutputStreamWriter(out, "UTF-8");
      w.write(content);
      w.close();
    } finally {
      out.close();
    }
  }

  private static String read(Path p) throws IOException {
    // TODO: this is not efficient for large files.  Maybe check whether
    // the input stream is buffered, and use the file size to preallocate
    // the StringBuilder.
    // Or just use the byte buffer stuff.
    StringBuilder out = new StringBuilder();
    InputStream in = p.newInputStream(StandardOpenOption.READ);
    try {
      Reader r = new InputStreamReader(in, "UTF-8");
      char[] buf = new char[4096];
      for (int n; (n = r.read(buf)) > 0;) { out.append(buf, 0, n); }
    } finally {
      in.close();
    }
    return out.toString();
  }
}
