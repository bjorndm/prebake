package org.prebake.js;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nonnull;

/**
 * Invokable from JavaScript to load another JavaScript module.
 *
 * @author mikesamuel@gmail.com
 */
public interface Loader {
  @Nonnull Executor.Input load(@Nonnull Path p) throws IOException;
}
