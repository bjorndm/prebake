package org.prebake.service;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A service that services requests by the {@link org.prebake.client.Bake}.
 *
 * @author mikesamuel@gmail.com
 */
public final class Prebakery {
  private final Config config;

  public Prebakery(Config config) {
    assert config != null;
    this.config = staticCopy(config);
  }

  public Config getConfig() { return config; }

  public void start(Runnable onShutdown) {
    // TODO: write me
    if (onShutdown != null) {
      onShutdown.run();
    }
  }

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
      @Override public Path getClientRoot() { return clientRoot; }
      @Override public Pattern getIgnorePattern() { return ignorePattern; }
      @Override public String getPathSeparator() { return pathSep; }
      @Override public Set<Path> getPlanFiles() { return planFiles; }
      @Override public List<Path> getToolDirs() { return toolDirs; }
      @Override public int getUmask() { return umask; }
    };
  }
}
