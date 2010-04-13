package org.prebake.os;

import java.nio.file.Path;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstraction over {@link ProcessBuilder} to ease testing.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface OperatingSystem {
  Process run(Path cwd, String command, String... argv);
  Path getTempDir();
}
