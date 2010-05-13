// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.prebake.service.tools;

import org.prebake.core.Hash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.Loader;
import org.prebake.service.PrebakeScriptLoader;

import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.ImmutableList;

/**
 * A custom loader that lets one tool file load the next tool file in the
 * tool lookup path.  This allows easy wrapping and extending of tools.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public class ToolLoader implements Loader {
  private final Path base;
  private final ToolBox toolBox;
  private final ToolImpl current;
  private final ImmutableList.Builder<Path> pathsLoaded;
  private final Hash.Builder hashes;

  public ToolLoader(
      Path base, ToolBox toolBox, ToolImpl current,
      ImmutableList.Builder<Path> pathsLoaded, Hash.Builder hashes) {
    this.base = base;
    this.toolBox = toolBox;
    this.current = current;
    this.pathsLoaded = pathsLoaded;
    this.hashes = hashes;
  }

  public Executor.Input load(Path p) throws IOException {
    // The name "next" resolves to the next instance of the
    // same tool in the search path.
    String pName = p.getName().toString();
    Path parent = p.getParent();
    // TODO: Allow ".../<tool-name>" to load another tool.
    if ("...".equals(pName) && base.equals(parent)) {
      try {
        p = toolBox.nextToolPath(current, base.resolve("..."));
      } catch (IOException ex) {
        // TODO: Need to make it depend on all the subsequent tools.
        pathsLoaded.add(p);
        hashes.withHash(FileVersioner.NO_FILE_HASH);
      }
    }
    return new PrebakeScriptLoader(
        toolBox.files, this.pathsLoaded, this.hashes).load(p);
  }
}
