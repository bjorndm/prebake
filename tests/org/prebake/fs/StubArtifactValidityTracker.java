package org.prebake.fs;

import org.prebake.core.Hash;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StubArtifactValidityTracker implements ArtifactValidityTracker {
  private final Path root;

  public StubArtifactValidityTracker(Path root) throws IOException {
    this.root = root.toRealPath(false);
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
        try {
          Hash h = Hash.builder().withFile(p).build();
          hashes.put(p, h);
        } catch (IOException ex) {
          // ignore
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public FileSystem getFileSystem() { return root.getFileSystem(); }

  public FileAndHash load(Path p) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    InputStream in = p.newInputStream();
    try {
      byte[] buf = new byte[4096];
      for (int n; (n = in.read(buf)) > 0;) { out.write(buf, 0, n); }
    } finally {
      in.close();
    }
    byte[] data = out.toByteArray();
    p = p.toRealPath(false);
    Hash h = p.startsWith(root) ? Hash.builder().withData(data).build() : null;
    return new FileAndHash(p, data, h);
  }

  public <T extends NonFileArtifact> boolean update(
      ArtifactAddresser<T> as, T artifact,
      Collection<Path> prereqs, Hash prereqHash) {
    Hash.Builder b = Hash.builder();
    List<Path> realPrereqs = Lists.newArrayList();
    for (Path p : prereqs) {
      try {
        p = p.toRealPath(false);
      } catch (IOException ex) {
        return false;
      }
      Hash h = hashes.get(p);
      if (h == null) {
        b = null;
        break;
      }
      b.withHash(h);
      realPrereqs.add(p);
    }
    if (b == null || !prereqHash.equals(b.build())) { return false; }
    artifacts.add(
        new ArtifactEntry<T>(as, as.addressFor(artifact), realPrereqs));
    return true;
  }

  private final Map<Path, Hash> hashes = Maps.newHashMap();
  private final List<ArtifactEntry<?>> artifacts = Lists.newLinkedList();

  public void update(Path... paths) throws IOException {
    Set<Path> changed = Sets.newHashSet();
    for (Path p : paths) {
      p = p.toRealPath(false);
      if (!p.startsWith(root)) { continue; }
      Hash h = Hash.builder().withFile(p).build();
      Hash old = hashes.put(p, h);
      if (old == null || !old.equals(h)) { changed.add(p); }
    }
    for (Iterator<ArtifactEntry<?>> it = artifacts.iterator(); it.hasNext(); ) {
      ArtifactEntry<?> e = it.next();
      Set<Path> deps = Sets.newHashSet(e.deps);
      deps.retainAll(changed);
      if (!deps.isEmpty()) {
        it.remove();
        fire(e);
      }
    }
  }

  private static <T extends NonFileArtifact> void fire(ArtifactEntry<T> e) {
    T artifact = e.addresser.lookup(e.address);
    artifact.markValid(false);
  }

  private static final class ArtifactEntry<T extends NonFileArtifact> {
    final ArtifactAddresser<T> addresser;
    final String address;
    final Set<Path> deps;

    ArtifactEntry(
        ArtifactAddresser<T> as, String address, Iterable<Path> deps) {
      this.addresser = as;
      this.address = address;
      this.deps = ImmutableSet.copyOf(deps);
    }
  }

  public void close() {}
}
