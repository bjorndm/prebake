package org.prebake.js;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nonnull;

public interface Loader {
  @Nonnull Executor.Input load(@Nonnull Path p) throws IOException;
}
