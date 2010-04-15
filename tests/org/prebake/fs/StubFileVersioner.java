package org.prebake.fs;

import org.prebake.core.Hash;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.sleepycat.je.OperationStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class StubFileVersioner extends FileVersioner {
  private final Map<Path, Hash> hashes = Maps.newHashMap();
  private final Multimap<Path, String> derivatives = Multimaps.newListMultimap(
      Maps.<Path, Collection<String>>newHashMap(),
      new Supplier<List<String>>() {
        public List<String> get() { return Lists.newArrayList(); }
      });

  public StubFileVersioner(Path root, Logger logger)
      throws IOException {
    super(root, logger);
  }

  @Override public void close() { /* noop */ }

  @Override
  protected ArtifactUpdateLoop makeArtifactUpdateLoop() {
    return new ArtifactUpdateLoopImpl();
  }

  @Override
  protected DerivativesLoop makeDerivativesLoop() {
    return new DerivativesLoopImpl();
  }

  @Override
  protected HashLoop makeHashLoop() {
    return new HashLoopImpl();
  }

  @Override
  protected RecordLoop makeRecordLoop() {
    return new RecordLoopImpl();
  }

  private final class RecordLoopImpl implements RecordLoop {
    Hash oldHash;
    Path keyPath;
    public void start() { oldHash = null; }
    public boolean find(Path keyPath) {
      this.keyPath = keyPath;
      this.oldHash = hashes.get(keyPath);
      return oldHash != null;
    }
    public byte[] currentHash() { return oldHash.toDatabaseEntry().getData(); }
    public boolean updateHash(Hash h) {
      hashes.put(keyPath, h);
      return true;
    }
    public boolean insert(Hash h) {
      hashes.put(keyPath, h);
      return true;
    }
    public boolean deleteCurrent() {
      return hashes.remove(keyPath) != null;
    }
    public void end() { oldHash = null; }
  }

  private final class DerivativesLoopImpl implements DerivativesLoop {
    private Iterator<String> addresses;
    public void start() { addresses = null; }
    public boolean findFirst(Path p) {
      addresses = Lists.newArrayList(derivatives.get(p)).iterator();
      return addresses.hasNext();
    }
    public boolean findNext() { return addresses.hasNext(); }
    public String getAddress() { return addresses.next(); }
    public void removeLast() { addresses.remove(); }
    public void end() { addresses = null; }
  }

  final class ArtifactUpdateLoopImpl implements ArtifactUpdateLoop {
    String artifactAddress;
    public void start(String artifactAddress) {
      this.artifactAddress = artifactAddress;
    }
    public void put(Path keyPath) {
      derivatives.put(keyPath, artifactAddress);
    }
    public void end() { artifactAddress = null; }
  }

  private final class HashLoopImpl implements HashLoop {
    private Hash h;
    public void start() { h = null; }
    public OperationStatus find(Path p) {
      h = hashes.get(p);
      if (h == null) { return OperationStatus.NOTFOUND; }
      return OperationStatus.SUCCESS;
    }
    public Hash getHash() { return h; }
    public void end() { h = null; }
  }

  @Override
  protected List<Path> pathsWithPrefix(
      String prefix, Predicate<String> predicate) {
    List<Path> paths;
    synchronized (hashes) {
      paths = Lists.newArrayList(hashes.keySet());
    }
    List<Path> out = Lists.newArrayList();
    for (Path p : paths) { if (predicate.apply(p.toString())) { out.add(p); } }
    return out;
  }
}
