package org.prebake.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuration for the {@link prebakery} service.
 * <p>
 * The getters below should consistently return the same value.
 */
public interface Config {
  /**
   * @see <a href="http://code.google.com/p/prebake/wiki/ClientRoot">wiki</a>
   */
  Path getClientRoot();
  /**
   * @see
   * <a href="http://code.google.com/p/prebake/wiki/IgnoredFileSet">wiki</a>
   */
  Pattern getIgnorePattern();
  /**
   * Permission bit mask for files created by the service.
   * @see <a href="http://code.google.com/p/prebake/wiki/Usage">usage</a>
   */
  int getUmask();
  /**
   * Search path for
   * <a href="http://code.google.com/p/prebake/wiki/ToolFile">tools</a>.
   */
  List<Path> getToolDirs();
  /** @see <a href="http://code.google.com/p/prebake/wiki/PlanFile">wiki</a> */
  Set<Path> getPlanFiles();
  /**
   * The string used to separate paths, e.g. <tt>:</tt> on *NIX systems or
   * <tt>;</tt> on Windows.
   * @see java.io.File#pathSeparator
   */
  String getPathSeparator();
}
