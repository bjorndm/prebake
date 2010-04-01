package org.prebake.js;

import java.io.IOException;
import java.nio.file.Path;

public interface Loader {
  Executor.Input load(Path p) throws IOException;
}
