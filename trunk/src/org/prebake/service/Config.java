package org.prebake.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration for the {@link Prebakery} service.
 * <p>
 * The getters below should consistently return the same value.
 *
 * @author mikesamuel@gmail.com
 */
public interface Config {
  /**
   * @see <a href="http://code.google.com/p/prebake/wiki/ClientRoot">wiki</a>
   */
  @Nullable Path getClientRoot();
  /**
   * @see
   * <a href="http://code.google.com/p/prebake/wiki/IgnoredFileSet">wiki</a>
   */
  @Nullable Pattern getIgnorePattern();
  /**
   * Permission bit mask for files created by the service.
   * @see <a href="http://code.google.com/p/prebake/wiki/Usage">usage</a>
   */
  int getUmask();
  /**
   * Search path for
   * <a href="http://code.google.com/p/prebake/wiki/ToolFile">tools</a>.
   */
  @Nonnull List<Path> getToolDirs();
  /** @see <a href="http://code.google.com/p/prebake/wiki/PlanFile">wiki</a> */
  @Nonnull Set<Path> getPlanFiles();
  /**
   * The string used to separate paths, e.g. <tt>:</tt> on *NIX systems or
   * <tt>;</tt> on Windows.
   * @see java.io.File#pathSeparator
   */
  @Nonnull String getPathSeparator();
}
