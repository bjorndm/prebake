package org.prebake.os;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstraction over {@link ProcessBuilder} to ease testing.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface OperatingSystem {
  Process run(String command, String... argv);
}
