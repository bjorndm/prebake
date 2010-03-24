package org.prebake.service;

import org.prebake.core.Hash;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class FileHashes implements Closeable {
  private final Database fileToHash;
  private final Path root;

  FileHashes(Environment env, Path root) throws IOException {
    this.root = root.toRealPath(false);
    DatabaseConfig dbConfig = new DatabaseConfig();
    dbConfig.setAllowCreate(true);
    dbConfig.setTemporary(true);
    // TODO: make sure no dupes
    fileToHash = env.openDatabase(null, "fileToHash", dbConfig);
  }

  void update(List<Path> toUpdate) {
    int n = toUpdate.size();
    Path[] relPaths = new Path[n];
    DatabaseEntry[] keys = new DatabaseEntry[n];
    DatabaseEntry[] hashes = new DatabaseEntry[n];
    {
      int i = 0;
      for (Path p : toUpdate) {
        try {
          relPaths[i] = p = p.toRealPath(false);
          keys[i] = new DatabaseEntry(
              root.relativize(p).toString().getBytes("UTF-8"));
          if (!p.notExists()) {
            Hash hash = new Hash.HashBuilder().withFile(p).toHash();
          hashes[i] = new DatabaseEntry(hash.getBytes());
          }
        } catch (IOException ex) {
          hashes[i] = null;
          ex.printStackTrace();
        }
      }
    }
    // Open a cursor using a database handle
    Cursor cursor = fileToHash.openCursor(null, null);
    try {
      for (int i = 0; i < n; ++i) {
        OperationStatus retVal = cursor.put(keys[i], hashes[i]);
        assert retVal == OperationStatus.SUCCESS;
      }
    } finally {
      cursor.close();
    }
  }

  public void getHashes(Iterable<Path> paths, Hash.HashBuilder out)
      throws IOException {
    // TODO: is there a way to batch gets?
    for (Path p : paths) {
      p = p.toRealPath(false);
      DatabaseEntry key = new DatabaseEntry(
          root.relativize(p).toString().getBytes("UTF-8"));
      DatabaseEntry value = new DatabaseEntry();
      OperationStatus status = fileToHash.get(
          null, key, value, LockMode.DEFAULT);
      switch (status) {
        case SUCCESS: out.withData(value.getData()); continue;
        case NOTFOUND: out.withData(NO_FILE); continue;
        case KEYEMPTY: case KEYEXIST: break;
      }
      throw new RuntimeException(status.name());
    }
  }

  private static final byte[] NO_FILE = new byte[2];

  public void close() { fileToHash.close(); }
}
