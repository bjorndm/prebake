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

import org.prebake.core.ArtifactListener;
import org.prebake.core.Hash;
import org.prebake.core.MessageQueue;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.FileVersioner;
import org.prebake.js.Executor;
import org.prebake.js.YSON;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKind;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Watches the set of tools available to plan files.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public class ToolBox implements ToolProvider {
  private final FileSystem fs;
  final FileVersioner files;
  private final ImmutableMap<String, ?> commonJsEnv;
  private final Logger logger;
  private final @Nullable WatchService watcher;
  private final ScheduledExecutorService execer;
  private final List<Path> toolDirs;
  private final Map<String, Tool> tools = Maps.newLinkedHashMap();
  private final Map<Path, Integer> dirIndices;
  private final ArtifactAddresser<ToolImpl> addresser
      = new ArtifactAddresser<ToolImpl>() {
        public String addressFor(ToolImpl artifact) {
          return artifact.tool.toolName + "#" + artifact.index;
        }

        public ToolImpl lookup(String address) {
          String name;
          int index;
          int hash = address.indexOf('#');
          name = address.substring(0, hash);
          index = Integer.parseInt(address.substring(hash + 1));
          return tools.get(name).impls.get(index);
        }
      };
  private final Future<?> updater;
  private final ArtifactListener<ToolSignature> listener;

  /**
   * An initialized but inactive tool-box.  Call {@link #start} to activate it.
   * @param files versions any tool files that are under the client directory.
   * @param commonJsEnv symbols available to tool files and plan files.
   * @param toolDirs the set of directories to search for tools.
   * @param logger receives messages about tool building progress.
   * @param listener is updated when tool definitions are invalidated or
   *     validated.
   * @param execer an executor which is used to schedule periodic maintenance
   *     tasks and which is used to update tool definitions.
   */
  public ToolBox(FileVersioner files, ImmutableMap<String, ?> commonJsEnv,
                 Iterable<Path> toolDirs, Logger logger,
                 ArtifactListener<ToolSignature> listener,
                 ScheduledExecutorService execer)
      throws IOException {
    this.logger = logger;
    toolDirs = this.toolDirs = ImmutableList.copyOf(Collections2.transform(
        Sets.newLinkedHashSet(toolDirs), new PathToRealPath()));
    this.fs = files.getFileSystem();
    this.files = files;
    this.commonJsEnv = commonJsEnv;
    this.listener = ArtifactListener.Factory.loggingListener(listener, logger);
    this.execer = execer;
    if (!this.toolDirs.isEmpty()) {
      for (Path toolDir : toolDirs) {
        if (fs != toolDir.getFileSystem()) {
          throw new IllegalArgumentException(
              "All tool directories must be in the same filesystem");
        }
      }
      this.watcher = fs.newWatchService();
      ImmutableMap.Builder<Path, Integer> b = ImmutableMap.builder();
      int index = 0;
      for (Path toolDir : this.toolDirs) { b.put(toolDir, index++); }
      this.dirIndices = b.build();
    } else {
      this.watcher = null;
      this.dirIndices = ImmutableMap.of();
    }
    // Load the builtin tools from a tools.txt file in this same directory.
    for (String builtin : getBuiltinToolNames()) { checkBuiltin(builtin); }

    this.updater = execer.scheduleWithFixedDelay(new Runnable() {
      public void run() { getAvailableToolSignatures(); }
    }, 1000, 1000, TimeUnit.MILLISECONDS);
  }

  /**
   * Makes the tool-box active.  After this is called, the tool-box will attempt
   * to keep tool definitions up-to-date.
   * @see #close
   */
  public void start() throws IOException {
    if (watcher != null) {
      final Map<WatchKey, Path> keyToDir = Maps.newHashMap();
      for (Path toolDir : this.toolDirs) {
        WatchKey key = toolDir.register(
            watcher,
            StandardWatchEventKind.ENTRY_CREATE,
            StandardWatchEventKind.ENTRY_DELETE,
            StandardWatchEventKind.ENTRY_MODIFY);
        keyToDir.put(key, toolDir);
      }
      for (Path toolDir : this.toolDirs) {
        logger.log(Level.FINE, "Looking in {0}", toolDir);
        for (Path child : toolDir.newDirectoryStream("*.js")) {
          checkUserProvided(toolDir, child.getName());
        }
      }

      Runnable r = new Runnable() {
        public void run() {
          WatchKey key;
          try {
            key = watcher.take();
          } catch (InterruptedException ex) {
            return;
          }

          Path dir = keyToDir.get(key);
          if (dir == null) { key.cancel(); return; }

          try {
            for (WatchEvent<?> event : key.pollEvents()) {
              WatchEvent.Kind<?> kind = event.kind();

              if (kind == StandardWatchEventKind.OVERFLOW) {
                // TODO: figure out what overflow means.
                continue;
              }

              // Context for directory entry event is the file name of entry
              Path name = (Path) event.context();
              checkUserProvided(dir, name);
            }
          } finally {
            // Reset key so that we can receive further events and remove it
            // from set if directory no longer accessible.
            boolean valid = key.reset();
            if (!valid) { keyToDir.remove(key); }
          }
        }
      };
      logger.log(Level.FINE, "Starting watcher");
      Thread th = new Thread(r);
      th.setDaemon(true);
      th.start();
    }
  }

  protected Iterable<String> getBuiltinToolNames() throws IOException {
    List<String> builtins = Lists.newArrayList();
    InputStream builtinFiles = ToolBox.class.getResourceAsStream("tools.txt");
    BufferedReader in = new BufferedReader(new InputStreamReader(
        builtinFiles, Charsets.UTF_8));
    try {
      for (String line; (line = in.readLine()) != null;) { builtins.add(line); }
    } finally {
      in.close();
    }
    return builtins;
  }

  /**
   * Gets the signatures of all available tools.
   * @return a list of promises to tool signatures.
   */
  public final List<Future<ToolSignature>> getAvailableToolSignatures() {
    List<Future<ToolSignature>> promises = Lists.newArrayList();
    List<Tool> tools;
    synchronized (this.tools) {
      tools = Lists.newArrayList(this.tools.values());
    }
    for (Tool t : tools) {
      synchronized (t) {
        Future<ToolSignature> f = t.validator;
        if (f == null) { f = t.validator = requireToolValid(t); }
        promises.add(f);
      }
    }
    return promises;
  }

  private static final Set<String> FREE_VARS_OK = ImmutableSet.<String>builder()
      .addAll(YSON.DEFAULT_YSON_ALLOWED)
      .add("load")
      .add("console")
      .build();

  private Future<ToolSignature> requireToolValid(final Tool t) {
    final ToolImpl impl;
    final int index;
    synchronized (tools) {
      Integer indexI = t.impls.firstKey();
      if (indexI == null) {
        index = -1;
        impl = null;
      } else {
        index = indexI;
        impl = t.impls.get(index);
      }
    }
    if (impl == null) {
      return new FutureTask<ToolSignature>(new NullResult());
    }
    return execer.submit(new Callable<ToolSignature>() {
      boolean clearedValidator;

      public ToolSignature call() {
        try {
          return tryToValidate(4);
        } finally {
          synchronized (impl.tool) {
            if (!clearedValidator) {
              impl.tool.validator = null;
              clearedValidator = true;
            }
          }
        }
      }

      private ToolSignature tryToValidate(int nTriesRemaining) {
        synchronized (impl.tool) {
          if (impl.isValid()) { return impl.sig; }
        }
        while (--nTriesRemaining >= 0) {
          ToolContent toolJs;
          if (index < 0) {
            logger.log(Level.SEVERE, "Tool {0} not on search path", t.toolName);
            return null;
          }
          try {
            toolJs = getTool(t);
          } catch (IOException ex) {
            logger.log(
                Level.SEVERE,
                "Reading tool " + t.localName + " from " + toolDirs.get(index),
                ex);
            return null;
          }
          final List<Path> paths = Lists.newArrayList();
          final List<Hash> hashes = Lists.newArrayList();
          Path toolPath = toolJs.fh.getPath();
          boolean isBuiltin = toolJs.isBuiltin;
          if (toolJs.fh.getHash() != null) {
            paths.add(toolPath);
            hashes.add(toolJs.fh.getHash());
          }
          Executor executor = Executor.Factory.createJsExecutor();
          ToolSignature toolSig;
          try {
            Executor.Input.Builder toolInput = Executor.Input.builder(
                toolJs.fh.getContentAsString(Charsets.UTF_8),
                toolPath.toString())
                .withActuals(commonJsEnv)
                .withBase(
                    isBuiltin
                    ? files.getVersionRoot().resolve(t.localName) : toolPath);
            Path base = isBuiltin ? null : toolDirs.get(index);
            Executor.Output<YSON> result = executor.run(
                YSON.class, logger,
                new ToolLoader(base, ToolBox.this, impl, paths, hashes),
                toolInput.build());
            if (result.exit == null) {
              MessageQueue mq = new MessageQueue();
              // We need content that we can safely serialize and load into a
              // plan file.
              YSON ysonSig = YSON.requireYSON(
                  result.result,
                  ImmutableSet.<String>builder()
                      .addAll(FREE_VARS_OK).addAll(commonJsEnv.keySet())
                      .build(),
                  new Predicate<String>() {
                    public boolean apply(String key) {
                      return !"fire".equals(key);
                    }
                  },
                  mq);

              toolSig = ToolSignature.converter(
                  t.toolName, !result.usedSourceOfKnownNondeterminism)
                  .convert(ysonSig != null ? ysonSig.toJavaObject() : null, mq);
              if (mq.hasErrors()) {
                for (String message : mq.getMessages()) {
                  // Escape message using MessageFormat rules.
                  logger.warning(MessageQueue.escape(message));
                }
                return null;
              }
            } else {
              logger.log(
                  Level.INFO, "Tool " + t.toolName + " failed with exception",
                  result.exit);
              return null;
            }
          } catch (RuntimeException ex) {
            logger.log(
                Level.INFO, "Tool " + t.toolName + " failed with exception",
                ex);
            return null;
          }

          Hash.Builder hb = Hash.builder();
          for (Hash h : hashes) {
            if (h != null) { hb.withHash(h); }
          }

          boolean success;
          synchronized (impl.tool) {
            success = files.update(addresser, impl, paths, hb.build());
            if (success) { impl.sig = toolSig; }
          }
          if (success) {
            t.check();
            return toolSig;
          }
          logger.log(
              Level.INFO, "Failed to update tool {0}.  {1} tries remaining",
              new Object[] { t.toolName, nTriesRemaining });
        }
        logger.log(Level.INFO, "Giving up on {0}", t.toolName);
        return null;
      }
    });
  }

  /**
   * Loads a tool file from disk.
   * @param name a tool name, not a file name so without any ".js" suffix.
   *     Tool names are resolved by looking for a file like
   *     {@code <tool_name>.js} in the tool directories passed to the
   *     constructor.
   */
  public ToolContent getTool(String name) throws IOException {
    Tool tool = tools.get(name);
    if (tool == null) {
      throw new FileNotFoundException("<tool>/" + name + ".js");
    }
    return getTool(tool);
  }

  private int indexForTool(Tool t) {
    Integer index = t.impls.firstKey();
    return index != null ? index : -1;
  }

  private ToolContent getTool(Tool t) throws IOException {
    int index = indexForTool(t);
    if (index < 0) {
      throw new FileNotFoundException("<tool>/" + t.localName.toString());
    }
    return getTool(t, index);
  }

  private ToolContent getTool(Tool t, int index) throws IOException {
    Path toolPath;
    InputStream jsIn;
    boolean underVersionRoot = false;
    boolean isBuiltin = isBuiltin(index);
    if (isBuiltin) {
      toolPath = t.localName;
      String resourceName = t.localName.toString();
      jsIn = ToolBox.class.getResourceAsStream(resourceName);
      if (jsIn == null) {
        throw new FileNotFoundException("<builtin>/" + resourceName);
      }
    } else {
      Path base = toolDirs.get(index);
      toolPath = base.resolve(t.localName);
      jsIn = toolPath.newInputStream();
      underVersionRoot = toolPath.startsWith(files.getVersionRoot());
    }
    return new ToolContent(
        FileAndHash.fromStream(toolPath, jsIn, underVersionRoot), isBuiltin);
  }

  private boolean isBuiltin(int index) {
    return index == toolDirs.size();
  }

  FileAndHash nextTool(ToolImpl ti, Path path) throws IOException {
    Tool tool = ti.tool;
    Integer nextIndex = null;
    for (Iterator<Integer> it = tool.impls.keySet().iterator(); it.hasNext();) {
      int index = it.next();
      if (index > ti.index) {
        nextIndex = index;
        break;
      }
    }
    if (nextIndex != null) {
      if (!isBuiltin(nextIndex)) {
        return files.load(toolDirs.get(nextIndex).resolve(tool.localName));
      } else {
        InputStream in = ToolBox.class.getResourceAsStream(
            tool.localName.toString());
        if (in != null) {
          return FileAndHash.fromStream(tool.localName, in, false);
        }
      }
    }
    throw new FileNotFoundException(path.toString());
  }

  /**
   * Shuts down all the external state created at {@link #start} time.
   */
  public final void close() throws IOException {
    if (watcher != null) { watcher.close(); }
    synchronized (tools) { tools.clear(); }
    updater.cancel(true);
  }

  private static @Nullable String toolName(String fileName) {
    if (!fileName.endsWith(".js")) { return null; }
    int len = fileName.length() - 3;
    if (len == 0) { return null; }
    String baseName = fileName.substring(0, len);
    if (!YSON.isValidIdentifier(baseName)) { return null; }
    return baseName;
  }

  private void checkBuiltin(String fileName) {
    logger.log(Level.FINE, "checking builtin {0}", fileName);
    InputStream in = ToolBox.class.getResourceAsStream(fileName);
    boolean exists;
    if (in != null) {
      try {
        in.close();
      } catch (IOException ex) {
        logger.log(Level.SEVERE, "Failed to read builtin " + fileName, ex);
      }
      exists = true;
    } else {
      logger.log(Level.WARNING, "Missing builtin {0}", fileName);
      exists = false;
    }
    check(dirIndices.size(), exists, fs.getPath(fileName));
  }

  private void checkUserProvided(Path dir, Path file) {
    Path fullPath = dir.resolve(file);
    logger.log(Level.FINE, "checking file {0}", fullPath);
    boolean exists = fullPath.exists();
    if (exists) {
      BasicFileAttributes attrs;
      try {
        attrs = Attributes.readBasicFileAttributes(fullPath);
      } catch (IOException ex) {
        // If we can't read the attributes, then we can't hash it either.
        return;
      }
      if (!attrs.isRegularFile()) { return; }
    }
    Integer dirIndex = dirIndices.get(dir);
    if (dirIndex == null) { return; }
    check(dirIndex, exists, file.getName());
  }

  private void check(int dirIndex, boolean exists, Path localName) {
    String toolName = toolName(localName.toString());
    synchronized (tools) {
      Tool tool = tools.get(toolName);
      if (exists) {
        if (tool == null) {
          tools.put(toolName, tool = new Tool(toolName, localName, listener));
        }
        if (!tool.impls.containsKey(dirIndex)) {
          tool.impls.put(dirIndex, new ToolImpl(tool, dirIndex));
        }
      } else if (tool != null) {
        tool.impls.remove(dirIndex);
        if (tool.impls.isEmpty()) { tools.remove(toolName); }
      }
    }
  }

  private static final class PathToRealPath implements Function<Path, Path> {
    public Path apply(Path p) {
      try {
        return p.toRealPath(false);
      } catch (IOException ex) {
        throw new IllegalArgumentException(p.toString(), ex);
      }
    }
  }

  private static final class NullResult implements Callable<ToolSignature> {
    public ToolSignature call() { return null; }
  }
}
