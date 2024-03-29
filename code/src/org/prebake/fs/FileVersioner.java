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

package org.prebake.fs;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.GlobSet;
import org.prebake.core.Hash;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sleepycat.je.OperationStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * As files changes, maintains a table of file hashes, and invalidates non-file
 * artifacts such as the toolbox, and dependency graph.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public abstract class FileVersioner {
  protected final Logger logger;
  protected final Path root;
  /** Paths to ignore. */
  private final Predicate<Path> toWatch;
  private final List<ArtifactAddresser<?>> addressers = Lists.newArrayList();
  private final Map<ArtifactAddresser<?>, Integer> addressersReverse
      = Maps.newIdentityHashMap();
  /**
   * Guards access to the derivative table so that we reliably invalidate
   * non-file-artifacts when their dependencies have changed and don't record
   * dependencies for non-file-artifacts if their dependencies have changed
   * since being derived.
   */
  private final ReadWriteLock derivativeHashLock
      = new ReentrantReadWriteLock(true);
  private final GlobDispatcher dispatcher;

  public FileVersioner(Path root, Predicate<Path> toWatch, Logger logger)
      throws IOException {
    this.logger = logger;
    this.toWatch = toWatch;
    this.root = root.toRealPath(false);
    this.dispatcher = new GlobDispatcher(logger);
  }

  public Path getVersionRoot() { return root; }

  public FileSystem getFileSystem() { return root.getFileSystem(); }

  public boolean isUnderVersionRoot(Path p) {
    return toKeyPath(p) != null;
  }

  protected final @Nullable Path toKeyPath(Path p) {
    try {
      Path relPath = root.relativize(p.toRealPath(false));
      if (relPath.isAbsolute()) { return null; }
      if (relPath.getNameCount() != 0
          && "..".equals(relPath.getName(0).toString())) {
        return null;
      }
      return toWatch.apply(relPath) ? relPath : null;
    } catch (IllegalArgumentException ex) {  // p under different root path
      return null;
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Failed to convert path to key " + p, ex);
      return null;
    }
  }

  public FileAndHash load(Path p) throws IOException {
    return load(Collections.singletonList(p)).get(0);
  }

  public List<FileAndHash> load(Iterable<Path> paths) throws IOException {
    ImmutableList.Builder<FileAndHash> out = ImmutableList.builder();
    for (Path p : paths) {
      p = p.toRealPath(false);
      InputStream in = p.newInputStream();
      FileAndHash fh = FileAndHash.fromStream(p, in, p.startsWith(root));
      Path kp = toKeyPath(p);
      if (kp == null) {
        fh = fh.withoutHash();
      } else {
        RecordLoop rl = makeRecordLoop();
        rl.start();
        try {
          if (rl.find(kp)) {
            if (!fh.getHash().matches(rl.currentHash())) {
              rl.updateHash(fh.getHash());
            }
          } else {
            rl.insert(fh.getHash());
          }
        } finally {
          rl.end();
        }
      }
      out.add(fh);
    }
    return out.build();
  }

  protected abstract List<Path> pathsWithPrefix(
      String prefix, Predicate<String> predicate);

  public List<Path> matching(GlobSet globs) {
    return pathsWithPrefix(
        Glob.commonPrefix(globs), new PathPredicate(Glob.toRegex(globs)));
  }

  private final static class PathPredicate implements Predicate<String> {
    private final Pattern p;
    PathPredicate(Pattern p) { this.p = p; }
    public boolean apply(String pathStr) {
      return p.matcher(pathStr).matches();
    }
  }

  public void unwatch(GlobUnion globs, ArtifactListener<GlobUnion> watcher) {
    dispatcher.unwatch(globs, watcher);
  }

  public void watch(GlobUnion globs, ArtifactListener<GlobUnion> watcher) {
    dispatcher.watch(globs, watcher);
  }

  protected static final class UpdateRecord {
    final Path keyPath;
    final Hash hash;
    UpdateRecord(Path keyPath, @Nullable Hash hash) {
      this.keyPath = keyPath;
      this.hash = hash;
    }
  }

  protected interface RecordLoop {
    void start();
    boolean find(Path keyPath);
    byte[] currentHash();
    boolean updateHash(Hash h);
    boolean insert(Hash h);
    boolean deleteCurrent();
    void end();
  }

  protected interface DerivativesLoop {
    void start();
    boolean findFirst(Path p);
    boolean findNext();
    String getAddress();
    void removeLast();
    void end();
  }

  protected abstract RecordLoop makeRecordLoop();
  protected abstract DerivativesLoop makeDerivativesLoop();

  // TODO: HIGH: move updates onto execer
  /** Called when the system is notified that the given files have changed. */
  public void updateFiles(Collection<Path> toUpdate) {
    int n = toUpdate.size();
    UpdateRecord[] records = new UpdateRecord[n];
    Iterator<Path> paths = toUpdate.iterator();
    for (int i = 0; i < n; ++i) {
      Path p = paths.next();
      Path keyPath = toKeyPath(p);
      if (keyPath == null) {
        logger.log(Level.FINE, "Not updating external file {0}", p);
        continue;
      }
      // Normalize the path failing if not under the root of watched files.
      Hash hash = null;
      try {
        if (!p.notExists()) {
          logger.log(Level.FINE, "Hashing file {0}", p);
          hash = Hash.builder().withFile(p).build();
        }
      } catch (IOException ex) {
        logger.log(Level.WARNING, "Failed to hash " + p, ex);
      }
      records[i] = new UpdateRecord(keyPath, hash);
    }

    // For each file, true if derivatives don't need to be invalidated.
    RecordLoop loop = makeRecordLoop();
    loop.start();
    List<UpdateRecord> changed = Lists.newArrayList();
    try {
      for (UpdateRecord r : records) {
        if (r == null) { continue; }
        Hash newHash = r.hash;
        boolean success;
        if (loop.find(r.keyPath)) {
          if (newHash != null) {
            if (!newHash.matches(loop.currentHash())) {
              changed.add(r);
              logger.log(Level.FINER, "Updating hash for {0}", r.keyPath);
              // The cursor is in the right place.  Just update the data.
              success = loop.updateHash(newHash);
            } else {
              success = true;
            }
          } else {
            changed.add(r);
            logger.log(Level.FINER, "Removing hash for {0}", r.keyPath);
            success = loop.deleteCurrent();
          }
        } else {  // Assume not found
          if (newHash != null) {
            changed.add(r);
            logger.log(Level.FINER, "Storing hash for  {0}", r.keyPath);
            success = loop.insert(r.hash);
          } else {
            success = true;
          }
        }
        assert success;
      }
    } finally {
      loop.end();
      loop = null;
    }

    // Figure out who to mark invalid, and remove rows corresponding to
    // soon-to-be-invalid objects.
    Set<String> addressesToInvalidate = Sets.newHashSet();
    derivativeHashLock.writeLock().lock();
    try {
      DerivativesLoop dloop = makeDerivativesLoop();
      dloop.start();
      try {
        for (UpdateRecord r : changed) {
          boolean found = dloop.findFirst(r.keyPath);
          while (found) {
            addressesToInvalidate.add(dloop.getAddress());
            dloop.removeLast();
            found = dloop.findNext();
          }
        }
      } finally {
        dloop.end();
      }
    } finally {
      derivativeHashLock.writeLock().unlock();
    }

    // Mark everyone invalid now.
    for (String address : addressesToInvalidate) {
      int colon = address.indexOf(':');
      try {
        int nsIndex = Integer.parseInt(address.substring(0, colon));
        ArtifactAddresser<?> as = addressers.get(nsIndex);
        if (as != null) {
          NonFileArtifact<?> inv = as.lookup(address.substring(colon + 1));
          if (inv != null) {
            logger.log(Level.FINER, "Invalidating {0}", address);
            inv.invalidate();
          }
        }
      } catch (RuntimeException ex) {
        logger.log(Level.SEVERE, "Failed to invalidate address " + address, ex);
      }
    }

    // Finally dispatch based on globs.
    if (!changed.isEmpty()) {
      List<Path> changedPaths = Lists.newArrayList();
      for (UpdateRecord r : changed) { changedPaths.add(r.keyPath); }
      dispatcher.dispatch(changedPaths);
    }
  }

  protected interface HashLoop {
    void start();
    Hash getHash();
    OperationStatus find(Path p);
    void end();
  }

  protected abstract HashLoop makeHashLoop();

  /**
   * Hashes the given paths to out.
   * @param out modified in place.
   */
  public void getHashes(Collection<Path> paths, Hash.Builder out) {
    int n = paths.size();
    HashLoop loop = makeHashLoop();
    Hash[] hashes = new Hash[n];
    loop.start();
    try {
      Iterator<Path> it = paths.iterator();
      for (int i = 0; i < n; ++i) {
        Path p = it.next();
        Path keyPath = toKeyPath(p);
        if (keyPath != null) {
          OperationStatus status = loop.find(keyPath);
          switch (status) {
            case SUCCESS: hashes[i] = loop.getHash(); continue;
            case NOTFOUND: continue;
            case KEYEMPTY: case KEYEXIST: break;
          }
          throw new RuntimeException(status.name());
        }
      }
    } finally {
      loop.end();
    }
    for (Hash h : hashes) {
      if (h != null) {
        out.withHash(h);
      } else {
        out.withData(NO_FILE);
      }
    }
  }

  /**
   * Register an address space that looks up non-file artifacts that depend on
   * file artifacts.
   */
  private int indexForAddresser(ArtifactAddresser<?> as) {
    synchronized(addressersReverse) {
      Integer index = addressersReverse.get(as);
      if (index == null) {
        index = addressers.size();
        addressersReverse.put(as, index);
        addressers.add(as);
      }
      return index;
    }
  }

  protected interface ArtifactUpdateLoop {
    void start(String artifactAddress);
    void put(Path keyPath);
    void end();
  }

  protected abstract ArtifactUpdateLoop makeArtifactUpdateLoop();

  public static final Hash NO_FILE_HASH = Hash.builder().build();
  /**
   * Checks that the files that an artifact depends on are still valid before
   * marking that artifact valid and recording the prerequisites so that the
   * artifact can be invalidated should any of the prerequisites subsequently
   * change.
   *
   * @param as the address space for item.
   * @param artifact a newly valid non file artifact that was derived from
   *    prerequisites.
   * @param value passed to {@link NonFileArtifact#validate} if the artifact
   *    can indeed be validated.
   * @param prerequisites the files on which item depends.
   * @param prereqHash the hash of prerequisites at the time artifact was
   *    derived from them.
   * @return true if item is really valid -- if its hash is up-to-date.
   *    If false, then one or more of the prerequisites has changed since
   *    artifact was derived.
   */
  public <X, T extends NonFileArtifact<X>> boolean updateArtifact(
      ArtifactAddresser<T> as, T artifact, @Nullable X value,
      Collection<Path> prerequisites, Hash prereqHash) {
    Set<Path> keyPaths = Sets.newHashSetWithExpectedSize(prerequisites.size());
    for (Path p : prerequisites) {
      // Normalize the path failing if not under the root of watched files.
      Path keyPath = toKeyPath(p);
      if (keyPath != null) { keyPaths.add(keyPath); }
    }

    Iterator<Path> it = keyPaths.iterator();
    if (!it.hasNext()) {  // No dependencies.
      if (NO_FILE_HASH.equals(prereqHash)) {
        artifact.validate(value);
        return true;
      } else {
        return false;
      }
    }

    int index = indexForAddresser(as);  // assumes addressers long lived
    assert addressers.get(index) == as;
    String address = index + ":" + as.addressFor(artifact);

    // Lock this for read so we can rehash and store the validity without
    // fearing that the file hash store will change in the meantime and fail to
    // invalidate the artifact.
    derivativeHashLock.readLock().lock();
    try {
      Hash.Builder rehash = Hash.builder();
      getHashes(prerequisites, rehash);
      if (!prereqHash.equals(rehash.build())) {
        logger.log(Level.INFO, "Version skew.  Cannot validate {0}", address);
        return false;
      }
      rehash = null;

      ArtifactUpdateLoop loop = makeArtifactUpdateLoop();
      loop.start(address);
      try {
        do {
          loop.put(it.next());
        } while (it.hasNext());
      } finally {
        loop.end();
      }
      artifact.validate(value);
      logger.log(Level.FINE, "Validated {0}", address);
      return true;
    } finally {
      derivativeHashLock.readLock().unlock();
    }
  }

  public abstract void close();

  private static final byte[] NO_FILE = new byte[2];

  @VisibleForTesting
  String unittestBackdoorDispatcherKeys() {
    return dispatcher.unittestBackdoorGlobKeys();
  }
}
