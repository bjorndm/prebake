package org.prebake.js;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

public interface Loader {
  Reader load(Path p) throws IOException;
}
