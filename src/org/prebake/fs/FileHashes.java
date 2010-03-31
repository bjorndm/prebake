package org.prebake.fs;

import org.prebake.core.Hash;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * As files changes, maintains a table of file hashes, and invalidates non-file
 * artifacts such as the toolbox, and dependency graph.
 *
 * @author mikesamuel@gmail.com
 */
public final class FileHashes implements Closeable {
  private final Logger logger;
  private final Database fileToHash;
  private final Database fileDerivatives;
  private final Path root;
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

  public FileHashes(Environment env, Path root, Logger logger)
      throws IOException {
    this.logger = logger;
    this.root = root.toRealPath(false);
    DatabaseConfig fileToHashConfig = new DatabaseConfig();
    fileToHashConfig.setAllowCreate(true);
    fileToHashConfig.setTemporary(true);
    fileToHashConfig.setSortedDuplicates(false);
    fileToHash = env.openDatabase(null, "fileToHash", fileToHashConfig);
    DatabaseConfig derivConfig = new DatabaseConfig();
    derivConfig.setAllowCreate(true);
    derivConfig.setTemporary(true);
    derivConfig.setSortedDuplicates(true);
    fileDerivatives = env.openDatabase(null, "fileDerivatives", derivConfig);
  }

  public FileSystem getFileSystem() { return root.getFileSystem(); }

  private Path toKeyPath(Path p) {
    try {
      Path relPath = root.relativize(p.toRealPath(false));
      return (relPath.getNameCount() == 0
              || !"..".equals(relPath.getName(0).toString())) ? relPath : null;
    } catch (IllegalArgumentException ex) {  // p under different root path
      return null;
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Failed to convert path to key " + p, ex);
      return null;
    }
  }

  /**
   * Called when the system is notified that the given files have changed.
   */
  public void update(List<Path> toUpdate) {
    int n = toUpdate.size();
    Path[] relPaths = new Path[n];
    // null elements correspond to bad inputs
    DatabaseEntry[] keys = new DatabaseEntry[n];
    // null elements correspond to bad input paths and for unreadable good paths
    DatabaseEntry[] hashes = new DatabaseEntry[n];
    Iterator<Path> paths = toUpdate.iterator();
    for (int i = 0; i < n; ++i) {
      Path p = paths.next();
      Path keyPath = relPaths[i] = toKeyPath(p);
      if (keyPath == null) {
        logger.log(Level.FINE, "Not updating external file {0}", p);
        continue;
      }
      // Normalize the path failing if not under the root of watched files.
      keys[i] = new DatabaseEntry(bytes(keyPath));
      try {
        if (!p.notExists()) {
          logger.log(Level.FINE, "Hashing file {0}", p);
          Hash hash = Hash.builder().withFile(p).toHash();
          hashes[i] = hash.toDatabaseEntry();
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }

    // For each file, true if derivatives don't need to be invalidated.
    BitSet unchanged = new BitSet(n);
    Cursor cursor = fileToHash.openCursor(null, null);
    try {
      DatabaseEntry result = new DatabaseEntry();
      for (int i = 0; i < n; ++i) {
        DatabaseEntry key = keys[i];
        if (key == null) {
          unchanged.set(i);
          continue;
        }
        DatabaseEntry newHash = hashes[i];
        OperationStatus retVal = cursor.getSearchKey(key, result, null);
        if (retVal == OperationStatus.SUCCESS) {
          if (newHash != null) {
            if (Arrays.equals(result.getData(), newHash.getData())) {
              // No change in the hash.
              unchanged.set(i);
            } else {
              logger.log(Level.FINER, "Updating hash for {0}", relPaths[i]);
              // The cursor is in the right place.  Just update the data.
              retVal = cursor.putCurrent(newHash);
            }
          } else {
            logger.log(Level.FINER, "Removing hash for {0}", relPaths[i]);
            retVal = cursor.delete();
          }
        } else {  // Assume not found
          if (newHash != null) {
            logger.log(Level.FINER, "Storing hash for  {0}", relPaths[i]);
            retVal = cursor.put(key, newHash);
          } else {
            unchanged.set(i);
            retVal = OperationStatus.SUCCESS;
          }
        }
        assert retVal == OperationStatus.SUCCESS;
      }
    } finally {
      cursor.close();
    }

    // Figure out who to mark invalid, and remove rows corresponding to
    // soon-to-be-invalid objects.
    Set<String> addressesToInvalidate = Sets.newHashSet();
    derivativeHashLock.writeLock().lock();
    try {
      cursor = fileDerivatives.openCursor(null, null);
      try {
        DatabaseEntry data = new DatabaseEntry();
        for (int i = -1; (i = unchanged.nextClearBit(i + 1)) < n;) {
          DatabaseEntry key = keys[i];
          OperationStatus retVal = cursor.getSearchKey(key, data, null);
          while (retVal == OperationStatus.SUCCESS) {
            addressesToInvalidate.add(fromBytes(data.getData()));
            cursor.delete();
            retVal = cursor.getNextDup(key, data, null);
          }
        }
      } finally {
        cursor.close();
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
          NonFileArtifact inv = as.lookup(address.substring(colon + 1));
          if (inv != null) {
            logger.log(Level.INFO /*TODO FINER*/, "Invalidating {0}", address);
            inv.markValid(false);
          }
        }
      } catch (RuntimeException ex) {
        logger.log(Level.SEVERE, "Failed to invalidate address " + address, ex);
      }
    }
  }

  /**
   * Hashes the given paths to out.
   */
  public void getHashes(Collection<Path> paths, Hash.HashBuilder out) {
    int n = paths.size();
    Cursor cursor = fileToHash.openCursor(null, null);
    byte[][] data = new byte[n][];
    try {
      DatabaseEntry result = new DatabaseEntry();
      Iterator<Path> it = paths.iterator();
      for (int i = 0; i < n; ++i) {
        Path p = it.next();
        try {
          p = p.toRealPath(false);
        } catch (IOException ex) {
          logger.log(Level.WARNING, "Cannot get hash for " + p, ex);
        }
        if (p == null) {
          data[i] = NO_FILE;
          continue;
        }
        Path keyPath = toKeyPath(p);
        if (keyPath == null) {
          data[i] = NO_FILE;
        } else {
          DatabaseEntry key = new DatabaseEntry(bytes(keyPath));
          OperationStatus status = cursor.getSearchKey(key, result, null);
          switch (status) {
            case SUCCESS: data[i] = result.getData(); continue;
            case NOTFOUND: data[i] = NO_FILE; continue;
            case KEYEMPTY: case KEYEXIST: break;
          }
          throw new RuntimeException(status.name());
        }
      }
    } finally {
      cursor.close();
    }
    for (byte[] bytes : data) { out.withData(bytes); }
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

  private static final Hash NO_FILE_HASH = Hash.builder().toHash();
  /**
   * @param artifact a newly valid non file artifact.
   * @param as the address space for item.
   * @param prerequisites the files on which item depends.
   * @param prereqHash the hash of prerequisites at the time item became valid.
   * @return true if item is really valid -- if its hash is up-to-date.
   */
  public <T extends NonFileArtifact> boolean update(
      ArtifactAddresser<? super T> as, T artifact,
      Collection<Path> prerequisites, Hash prereqHash) {
    Set<Path> keyPaths;
    {
      int n = prerequisites.size();
      keyPaths = Sets.newHashSetWithExpectedSize(n);
      Path parent = root.getFileSystem().getPath("..");
      Iterator<Path> paths = prerequisites.iterator();
      for (int i = 0; i < n; ++i) {
        Path p = paths.next();
        // Normalize the path failing if not under the root of watched files.
        try {
          Path keyPath = root.relativize(p.toRealPath(false));
          if (keyPath.getNameCount() != 0
              && parent.equals(keyPath.getName(0))) {
            logger.log(Level.FINER, "Skipping ext prerequisite {0}", p);
          } else {
            keyPaths.add(keyPath);
          }
        } catch (IllegalArgumentException ex) {  // p under different root path
          continue;
        } catch (IOException ex) {
          continue;
        }
      }
    }

    Iterator<Path> it = keyPaths.iterator();
    if (!it.hasNext()) {  // No dependencies.
      if (NO_FILE_HASH.equals(prereqHash)) {
        artifact.markValid(true);
        return true;
      } else {
        return false;
      }
    }

    // Lock this for read so we can rehash and store the validity without
    // fearing that the file hash store will change in the meantime and fail to
    // invalidate the artifact.
    derivativeHashLock.readLock().lock();
    try {
      int index = indexForAddresser(as);  // assumes addressers long lived
      assert addressers.get(index) == as;
      String address = index + ":" + as.addressFor(artifact);
      DatabaseEntry value = new DatabaseEntry(bytes(address));

      Hash.HashBuilder rehash = Hash.builder();
      getHashes(prerequisites, rehash);
      if (!prereqHash.equals(rehash.toHash())) {
        logger.log(Level.INFO, "Version skew.  Cannot validate {0}", address);
        return false;
      }
      rehash = null;

      DatabaseEntry key = new DatabaseEntry();
      Cursor cursor = fileDerivatives.openCursor(null, null);
      try {
        do {
          key.setData(bytes(it.next()));
          cursor.put(key, value);
        } while (it.hasNext());
      } finally {
        cursor.close();
      }
      artifact.markValid(true);
      logger.log(Level.FINE, "Validated {0}", address);
      return true;
    } finally {
      derivativeHashLock.readLock().unlock();
    }
  }

  private static final byte[] NO_FILE = new byte[2];

  public void close() {
    fileToHash.close();
    fileDerivatives.close();
  }

  private static final Charset UTF8 = Charset.forName("UTF-8");

  private static byte[] bytes(Path p) { return bytes(p.toString()); }

  private static byte[] bytes(String s) { return s.getBytes(UTF8); }

  private static String fromBytes(byte[] bytes) {
    return new String(bytes, UTF8);
  }
}
