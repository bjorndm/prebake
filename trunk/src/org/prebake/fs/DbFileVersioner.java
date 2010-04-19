package org.prebake.fs;

import org.prebake.core.Hash;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * As files changes, maintains a table of file hashes, and invalidates non-file
 * artifacts such as the toolbox, and dependency graph.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class DbFileVersioner extends FileVersioner {
  private final Database fileToHash;
  private final Database fileDerivatives;

  public DbFileVersioner(
      Environment env, Path root, Predicate<Path> toWatch, Logger logger)
      throws IOException {
    super(root, toWatch, logger);
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

  @Override
  protected List<Path> pathsWithPrefix(
      String commonPrefix, Predicate<String> predicate) {
    ImmutableList.Builder<Path> b = ImmutableList.builder();
    byte[] prefixBytes = commonPrefix.getBytes(Charsets.UTF_8);
    DatabaseEntry key = new DatabaseEntry();
    DatabaseEntry data = new DatabaseEntry();
    key.setData(prefixBytes);
    FileSystem fs = getFileSystem();
    Cursor cursor = fileToHash.openCursor(null, null);
    try {
      if (cursor.getSearchKeyRange(key, data, null)
          == OperationStatus.SUCCESS) {
        do {
          byte[] result = key.getData();
          if (!hasPrefix(result, prefixBytes)) { break; }
          String path = new String(result, Charsets.UTF_8);
          if (predicate.apply(path)) { b.add(fs.getPath(path)); }
        } while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS);
        // TODO: other operation statuses?
      }
    } finally {
      cursor.close();
    }
    return b.build();
  }

  private static boolean hasPrefix(byte[] arr, byte[] prefix) {
    int n = prefix.length;
    if (arr.length < n) { return false; }
    for (int i = n; --i >= 0;) {
      if (arr[i] != prefix[i]) { return false; }
    }
    return true;
  }

  @Override
  protected RecordLoop makeRecordLoop() { return new RecordLoopImpl(); }

  private final class RecordLoopImpl implements RecordLoop {
    private Cursor cursor;
    private DatabaseEntry key;
    private DatabaseEntry result;
    public void start() {
      cursor = fileToHash.openCursor(null, null);
      key = new DatabaseEntry();
      result = new DatabaseEntry();
    }
    public boolean find(Path keyPath) {
      key.setData(bytes(keyPath));
      return cursor.getSearchKey(key, result, null) == OperationStatus.SUCCESS;
    }
    public byte[] currentHash() { return result.getData(); }
    public boolean updateHash(Hash h) {
      return cursor.putCurrent(h.toDatabaseEntry()) == OperationStatus.SUCCESS;
    }
    public boolean insert(Hash h) {
      return cursor.put(key, h.toDatabaseEntry()) == OperationStatus.SUCCESS;
    }
    public boolean deleteCurrent() {
      return cursor.delete() == OperationStatus.SUCCESS;
    }
    public void end() { cursor.close(); }
  }

  @Override
  protected DerivativesLoop makeDerivativesLoop() {
    return new DerivativesLoopImpl();
  }

  private final class DerivativesLoopImpl implements DerivativesLoop {
    private Cursor cursor;
    private DatabaseEntry key, data;
    public void start() {
      cursor = fileDerivatives.openCursor(null, null);
      key = new DatabaseEntry();
      data = new DatabaseEntry();
    }
    public boolean findFirst(Path p) {
      key.setData(bytes(p));
      return cursor.getSearchKey(key, data, null) == OperationStatus.SUCCESS;
    }
    public boolean findNext() {
      return cursor.getNextDup(key, data, null) == OperationStatus.SUCCESS;
    }
    public String getAddress() { return fromBytes(data.getData()); }
    public void removeLast() { cursor.delete(); }
    public void end() { cursor.close(); }
  }

  @Override
  protected ArtifactUpdateLoop makeArtifactUpdateLoop() {
    return new ArtifactUpdateLoopImpl();
  }

  final class ArtifactUpdateLoopImpl implements ArtifactUpdateLoop {
    private Cursor cursor;
    private DatabaseEntry key;
    private DatabaseEntry value;

    public void start(String artifactAddress) {
      cursor = fileDerivatives.openCursor(null, null);
      key = new DatabaseEntry();
      value = new DatabaseEntry(bytes(artifactAddress));
    }
    public void put(Path keyPath) {
      key.setData(bytes(keyPath));
      cursor.put(key, value);
    }
    public void end() {
      cursor.close();
    }
  }

  @Override
  protected HashLoop makeHashLoop() { return new HashLoopImpl(); }

  private final class HashLoopImpl implements HashLoop {
    private Cursor cursor;
    private DatabaseEntry key, result;
    public void start() {
      cursor = fileToHash.openCursor(null, null);
      key = new DatabaseEntry();
      result = new DatabaseEntry();
    }
    public Hash getHash() { return Hash.fromDatabaseEntry(result); }
    public OperationStatus find(Path p) {
      key.setData(bytes(p));
      return cursor.getSearchKey(key, result, null);
    }
    public void end() { cursor.close(); }
  }

  @Override
  public void close() {
    fileToHash.close();
    fileDerivatives.close();
  }

  private static byte[] bytes(Path p) { return bytes(p.toString()); }

  private static byte[] bytes(String s) { return s.getBytes(Charsets.UTF_8); }

  private static String fromBytes(byte[] bytes) {
    return new String(bytes, Charsets.UTF_8);
  }

}
