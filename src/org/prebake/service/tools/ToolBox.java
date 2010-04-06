package org.prebake.service.tools;

import org.prebake.core.Hash;
import org.prebake.core.MessageQueue;
import org.prebake.fs.ArtifactAddresser;
import org.prebake.fs.ArtifactValidityTracker;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.NonFileArtifact;
import org.prebake.js.Executor;
import org.prebake.js.Loader;
import org.prebake.js.YSON;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKind;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the set of tools available to plan files.
 *
 * @author mikesamuel@gmail.com
 */
public class ToolBox implements ToolProvider {
  private final FileSystem fs;
  private final ArtifactValidityTracker fh;
  private final Logger logger;
  private final WatchService watcher;
  private final ScheduledExecutorService execer;
  private final List<Path> toolDirs;
  private final Map<String, Tool> tools = Maps.newLinkedHashMap();
  private final Map<Path, Integer> dirIndices;
  private final ArtifactAddresser<ToolImpl> addresser
      = new ArtifactAddresser<ToolImpl>() {
        public String addressFor(ToolImpl artifact) {
          return artifact.name + "#" + artifact.index;
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

  public ToolBox(ArtifactValidityTracker fh, Iterable<Path> toolDirs,
                 Logger logger, ScheduledExecutorService execer)
      throws IOException {
    this.logger = logger;
    toolDirs = this.toolDirs = ImmutableList.copyOf(
        Sets.newLinkedHashSet(toolDirs));
    this.fs = fh.getFileSystem();
    this.fh = fh;
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
      dirIndices = b.build();
    } else {
      watcher = null;
      dirIndices = ImmutableMap.of();
    }
    // Load the builtin tools from a tools.txt file in this same directory.
    for (String builtin : getBuiltinToolNames()) { checkBuiltin(builtin); }

    this.updater = execer.scheduleWithFixedDelay(new Runnable() {
      public void run() { getAvailableToolSignatures(); }
    }, 1000, 1000, TimeUnit.MILLISECONDS);
  }

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
          } catch (InterruptedException x) {
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
    BufferedReader in = new BufferedReader(
        new InputStreamReader(builtinFiles, UTF8));
    try {
      for (String line; (line = in.readLine()) != null;) { builtins.add(line); }
    } finally {
      in.close();
    }
    return builtins;
  }

  public final List<Future<ToolSignature>> getAvailableToolSignatures() {
    List<Future<ToolSignature>> promises = Lists.newArrayList();
    for (Tool t : tools.values()) {
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
    final String name = toolName(t.localName.toString());
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
      return new FutureTask<ToolSignature>(new Callable<ToolSignature>() {
        public ToolSignature call() { return null; }
      });
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
          Path toolPath = null;
          InputStream jsIn;
          final Path base;
          String js;
          try {
            if (index == toolDirs.size()) {  // builtin
              base = null;
              toolPath = t.localName;
              jsIn = ToolBox.class.getResourceAsStream(t.localName.toString());
              if (jsIn == null) { return null; }
            } else {
              base = toolDirs.get(index);
              toolPath = base.resolve(t.localName);
              jsIn = toolPath.newInputStream();
            }
            Reader r = new InputStreamReader(jsIn, UTF8);
            try {
              char[] buf = new char[4096];
              StringBuilder sb = new StringBuilder();
              for (int n; (n = r.read(buf)) > 0;) { sb.append(buf, 0, n); }
              js = sb.toString();
            } finally {
              try {
                r.close();
              } catch (IOException ex) {
                logger.log(Level.WARNING, "Closing " + toolPath, ex);
              }
            }
          } catch (IOException ex) {
            logger.log(Level.SEVERE, "Reading " + toolPath, ex);
            return null;
          }
          final List<Path> paths = Lists.newArrayList();
          final List<Hash> hashes = Lists.newArrayList();
          if (base != null) {
            paths.add(toolPath);
            hashes.add(Hash.builder().withString(js).build());
          }
          Executor executor;
          try {
            executor = Executor.Factory.createJsExecutor(Executor.Input.builder(
                js, toolPath).build());
          } catch (Executor.MalformedSourceException ex) {
            logger.log(Level.SEVERE, "Bad tool file " + toolPath, ex);
            return null;
          }
          ToolSignature toolSig;
          try {
            Executor.Output<YSON> result = executor.run(
                YSON.class, logger, new Loader() {
                  public Executor.Input load(Path p) throws IOException {
                    FileAndHash loaded;
                    try {
                      // The name "next" resolves to the next instance of the
                      // same tool in the search path.
                      if ("next".equals(p.getName().toString())
                          && base.equals(p.getParent())) {
                        loaded = nextTool(t, index, base.resolve("next"));
                      } else {
                        loaded = fh.load(p);
                      }
                    } catch (IOException ex) {
                      // We need to depend on non-existent files in case they
                      // are later created.
                      paths.add(p);
                      throw ex;
                    }
                    Hash h = loaded.getHash();
                    if (h != null) {
                      paths.add(loaded.getPath());
                      hashes.add(h);
                    }
                    return Executor.Input.builder(
                        loaded.getContentAsString(UTF8), p)
                        .build();
                  }
                });
            MessageQueue mq = new MessageQueue();
            // We need content that we can safely serialize and load into a plan
            // file.
            YSON ysonSig = YSON.requireYSON(result.result, FREE_VARS_OK, mq);

            Object ysonSigObj = ysonSig != null ? ysonSig.toJavaObject() : null;
            toolSig = ToolSignature.converter(
                impl.name, !result.usedSourceOfKnownNondeterminism)
                .convert(ysonSigObj, mq);
            if (mq.hasErrors()) {
              for (String message : mq.getMessages()) {
                // Escape message using MessageFormat rules.
                logger.warning("'" + message.replace("'", "''") + "'");
              }
              return null;
            }
          } catch (Executor.AbnormalExitException ex) {
            logger.log(
                Level.INFO, "Tool " + name + " failed with exception", ex);
            return null;
          } catch (RuntimeException ex) {
            logger.log(
                Level.INFO, "Tool " + name + " failed with exception", ex);
            return null;
          }

          Hash.Builder hb = Hash.builder();
          for (Hash h : hashes) {
            if (h != null) { hb.withHash(h); }
          }

          synchronized (impl.tool) {
            if (fh.update(addresser, impl, paths, hb.build())) {
              return impl.sig = toolSig;
            }
          }
          logger.log(
              Level.INFO, "Failed to update tool {0}.  {1} tries remaining",
              new Object[] { impl.name, nTriesRemaining });
        }
        logger.log(Level.INFO, "Giving up on {0}", impl.name);
        return null;
      }
    });
  }

  private FileAndHash nextTool(Tool t, int prevIndex, Path path)
      throws IOException {
    Integer nextIndex = null;
    for (Iterator<Integer> it = t.impls.keySet().iterator(); it.hasNext();) {
      int index = it.next();
      if (index > prevIndex) {
        nextIndex = index;
        break;
      }
    }
    if (nextIndex != null) {
      if (nextIndex < toolDirs.size()) {
        return fh.load(toolDirs.get(nextIndex).resolve(t.localName));
      } else {
        byte[] buf = new byte[4096];
        InputStream in = ToolBox.class.getResourceAsStream(
            t.localName.toString());
        if (in != null) {
          try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int n; (n = in.read(buf)) > 0;) { bytes.write(buf, 0, n); }
            return new FileAndHash(t.localName, bytes.toByteArray(), null);
          } finally {
            in.close();
          }
        }
      }
    }
    throw new FileNotFoundException(path.toString());
  }

  public final void close() throws IOException {
    if (watcher != null) { watcher.close(); }
    synchronized (tools) { tools.clear(); }
    tools.clear();
    updater.cancel(true);
  }

  private static String toolName(String fileName) {
    if (!fileName.endsWith(".js")) { return null; }
    int len = fileName.length() - 3;
    if (len == 0) { return null; }
    String baseName = fileName.substring(0, len);
    if (!Character.isJavaIdentifierStart(baseName.charAt(0))) { return null; }
    for (int i = 1; i < len; ++i) {
      if (!Character.isJavaIdentifierPart(baseName.charAt(i))) { return null; }
    }
    if (!Normalizer.isNormalized(baseName, Normalizer.Form.NFC)) {
      return null;
    }
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
        if (tool == null) { tools.put(toolName, tool = new Tool(localName)); }
        if (!tool.impls.containsKey(dirIndex)) {
          tool.impls.put(dirIndex, new ToolImpl(tool, toolName, dirIndex));
        }
      } else if (tool != null) {
        tool.impls.remove(dirIndex);
        if (tool.impls.isEmpty()) { tools.remove(toolName); }
      }
    }
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");
}

final class Tool {
  final Path localName;
  final SortedMap<Integer, ToolImpl> impls = Maps.newTreeMap();
  Future<ToolSignature> validator;

  Tool(Path localName) { this.localName = localName; }
}

final class ToolImpl implements NonFileArtifact {
  final Tool tool;
  final String name;
  final int index;
  ToolSignature sig;
  private boolean valid;

  ToolImpl(Tool tool, String name, int index) {
    this.tool = tool;
    this.name = name;
    this.index = index;
  }

  public boolean isValid() { return valid; }

  public void markValid(boolean valid) {
    synchronized (tool) {
      this.valid = valid;
      if (!valid) {
        sig = null;
        if (tool.validator != null) {
          tool.validator.cancel(false);
          tool.validator = null;
        }
      }
    }
  }
}