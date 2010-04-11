package org.prebake.service.tools;

import org.prebake.core.Hash;
import org.prebake.fs.FileAndHash;
import org.prebake.js.Executor;
import org.prebake.js.Loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.google.common.base.Charsets;

public class ToolLoader implements Loader{
  private final Path base;
  private final ToolBox toolBox;
  private final ToolImpl current;
  private final List<? super Path> pathsLoaded;
  private final List<? super Hash> hashes;

  public ToolLoader(
      Path base, ToolBox toolBox, ToolImpl current,
      List<? super Path> pathsLoaded, List<? super Hash> hashes) {
    this.base = base;
    this.toolBox = toolBox;
    this.current = current;
    this.pathsLoaded = pathsLoaded;
    this.hashes = hashes;
  }

  public Executor.Input load(Path p) throws IOException {
    FileAndHash loaded;
    try {
      // The name "next" resolves to the next instance of the
      // same tool in the search path.
      String pName = p.getName().toString();
      Path parent = p.getParent();
      // TODO: Allow ".../<tool-name>" to load another tool.
      if (base != null && "...".equals(pName) && base.equals(parent)) {
        loaded = toolBox.nextTool(current, base.resolve("..."));
      } else {
        loaded = toolBox.files.load(p);
      }
    } catch (IOException ex) {
      // We need to depend on non-existent files in case they
      // are later created.
      pathsLoaded.add(p);
      throw ex;
    }
    Hash h = loaded.getHash();
    if (h != null) {
      pathsLoaded.add(loaded.getPath());
      hashes.add(h);
    }
    return Executor.Input.builder(
        loaded.getContentAsString(Charsets.UTF_8), p)
        .build();
  }
}
