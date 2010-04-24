package org.prebake.os;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstraction over {@link ProcessBuilder} to ease testing.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface OperatingSystem {
  /**
   * @param cwd the working directory for the process.
   */
  Process run(Path cwd, String command, String... argv) throws IOException;
  /**
   * A path to a directory where temporary files and directories can be put.
   * It should be on a mount that has enough space to mirror large chunks of
   * the client directory.
   *
   * <p>Normally, this is the directory named in the System property
   * {@code java.io.tmpdir}.
   */
  Path getTempDir();
}
