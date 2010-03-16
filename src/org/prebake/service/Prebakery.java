package org.prebake.service;

import org.prebake.channel.Command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * A service that services requests by the {@link org.prebake.client.Bake}.
 *
 * @author mikesamuel@gmail.com
 */
public abstract class Prebakery {
  private final Config config;
  private final String token;
  private final LinkedBlockingQueue<Command> cmdQueue;

  public Prebakery(Config config) {
    assert config != null;
    this.config = staticCopy(config);
    this.token = makeToken();
    this.cmdQueue = new LinkedBlockingQueue<Command>(4);
  }

  public Config getConfig() { return config; }

  protected abstract int openChannel(int portHint, BlockingQueue<Command> q)
      throws IOException;

  protected abstract String makeToken();

  public void start(Runnable onShutdown) {
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
      if (!setupSucceeded && onShutdown != null) { onShutdown.run(); }
    }
  }

  /**
   * Connects the command queue to the outside world and sets up a directory
   * containing the information that clients need to connect.
   */
  private void setupChannel() throws IOException {
    Path clientRoot = config.getClientRoot();
    FileSystem fs = clientRoot.getFileSystem();
    Path dir = clientRoot.resolve(fs.getPath(".prebake"));
    if (!dir.exists()) {
      dir.createDirectory();  // TODO umask
    }
    Path cmdlineFile = dir.resolve(fs.getPath("cmdline"));
    Path portFile = dir.resolve(fs.getPath("port"));
    Path tokenFile = dir.resolve(fs.getPath("token"));
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
  }

  private void setupFileSystemWatcher() {
    // TODO
  }

  private void setupCommandHandler() {
    // TODO
  }

  /**
   * A {@code Config} snapshot that dodges problems that might arise from
   * mutable {@code Config}s.
   */
  private static Config staticCopy(Config config) {
    final Path clientRoot = config.getClientRoot();
    final Pattern ignorePattern = config.getIgnorePattern();
    final String pathSep = config.getPathSeparator();
    final Set<Path> planFiles = Collections.unmodifiableSet(
        new LinkedHashSet<Path>(config.getPlanFiles()));
    final List<Path> toolDirs = Collections.unmodifiableList(
        new ArrayList<Path>(config.getToolDirs()));
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
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      Writer w = new OutputStreamWriter(out, "UTF-8");
      w.write(content);
      w.close();
    } finally {
      out.close();
    }
  }

  private static String read(Path p) throws IOException {
    ReadableByteChannel in = p.newByteChannel(StandardOpenOption.READ);
    ByteBuffer bb = ByteBuffer.allocate(1024);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int n; (n = in.read(bb)) >= 0;) {
      out.write(bb.array(), 0, n);
      bb.clear();
    }
    return out.toString("UTF-8");
  }
}
