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

package org.prebake.core;

import org.prebake.util.PbTestCase;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.junit.Test;

public class GlobSetTest extends PbTestCase {
  @Test public final void testGlobSet() {
    long fastTime = 0;
    long simpleTime = 0;
    for (int run = 50; --run >= 0;) {
      long seed = System.currentTimeMillis();
      try {
        GlobSet fast = new GlobSet();
        SimpleGlobSet simple = new SimpleGlobSet();
        Random rnd = new Random(seed);
        Glob[] globs = new Glob[100];
        for (int i = globs.length; --i >= 0;) { globs[i] = randomGlob(rnd); }
        for (int op = 1000; --op >= 0;) {
          switch (rnd.nextInt(5)) {
            case 0: case 1: {
              Glob g = globs[rnd.nextInt(globs.length)];
              long t0 = System.nanoTime();
              simple.add(g);
              long t1 = System.nanoTime();
              fast.add(g);
              long t2 = System.nanoTime();
              simpleTime += t1 - t0;
              fastTime += t2 - t1;
              break;
            }
            case 2: {
              Glob g = globs[rnd.nextInt(globs.length)];
              long t0 = System.nanoTime();
              boolean simpleRemoved = simple.remove(g);
              long t1 = System.nanoTime();
              boolean fastRemoved = fast.remove(g);
              long t2 = System.nanoTime();
              assertEquals(simpleRemoved, fastRemoved);
              simpleTime += t1 - t0;
              fastTime += t2 - t1;
              break;
            }
            case 3: case 4: {
              Path p = randomPath(rnd);
              long t0 = System.nanoTime();
              Set<Glob> expected = Sets.newTreeSet(simple.matching(p));
              long t1 = System.nanoTime();
              Set<Glob> actual = Sets.newTreeSet(fast.matching(p));
              long t2 = System.nanoTime();
              if (!actual.equals(expected)) {
                assertEquals("" + p, expected, actual);
              }
              simpleTime += t1 - t0;
              fastTime += t2 - t1;
              break;
            }
          }
        }
      } catch (Throwable th) {
        System.err.println("seed=" + seed);
        Throwables.propagate(th);
      }
    }
    // Saves about 33% on the naive case, and should perform better where path
    // and glob prefixes&suffixes cluster more tightly as in real file systems.
    // TODO: verify this assertion of better performance on real data.
    System.err.println("simple took " + simpleTime + " ns");
    System.err.println("fast took   " + fastTime + " ns");
    System.err.println(
        String.format("%5.2f%%", ((double) fastTime) / simpleTime));
  }

  @Test public final void testGroupByPrefix() {
    GlobSet gset = new GlobSet();
    gset.add(Glob.fromString("/foo/bar/*.java"));
    gset.add(Glob.fromString("/foo/bar/*.html"));
    gset.add(Glob.fromString("/foo/**/*.txt"));
    gset.add(Glob.fromString("**.css"));
    assertEquals(
        (
         ""
         + "{"
         + "=[**.css], "
         + "foo=[/foo/**/*.txt], "
         + "foo/bar=[/foo/bar/*.html, /foo/bar/*.java]"
         + "}"
         ),
        "" + gset.getGlobsGroupedByPrefix());
  }

  @Test public final void testExactPath() {
    GlobSet gset = new GlobSet();
    gset.add(Glob.fromString("foo/bar/Foo.java"));
    gset.add(Glob.fromString("baz/*.html"));
    assertTrue(gset.matches(new StubPath("foo/bar/Foo.java")));
    assertFalse(gset.matches(new StubPath("foo/bar/Bar.java")));
  }

  private static final String[] WORDS = {
    "foo", "bar", "baz", "boo", "far", "faz",
    "FOO", "BAR", "BAZ", "BOO", "FAR", "FAZ"
  };

  private static Glob randomGlob(Random rnd) {
    StringBuilder sb = new StringBuilder();
    int n = rnd.nextInt(8) + 1;
    for (int i = 0; i < n; ++i) {
      switch (rnd.nextInt(5)) {
        case 0: sb.append(WORDS[rnd.nextInt(WORDS.length)]); break;
        case 1:
          if (i != 0) { sb.append('/'); }
          sb.append('*');
          break;
        case 2:
          if (i != 0) { sb.append('/'); }
          sb.append("**");
          break;
        case 3:
          if (i != 0) { sb.append('/'); }
          sb.append(WORDS[rnd.nextInt(WORDS.length)]); break;
        case 4: sb.append('.').append(WORDS[rnd.nextInt(WORDS.length)]); break;
      }
    }
    return Glob.fromString(sb.toString());
  }

  private Path randomPath(Random rnd) {
    StringBuilder sb = new StringBuilder();
    int n = rnd.nextInt(8);
    for (int i = 0; i < n; ++i) {
      switch (rnd.nextInt(3)) {
        case 0: sb.append(WORDS[rnd.nextInt(WORDS.length)]); break;
        case 1:
          if (i != 0) { sb.append('/'); }
          sb.append(WORDS[rnd.nextInt(WORDS.length)]); break;
        case 2: sb.append('.').append(WORDS[rnd.nextInt(WORDS.length)]); break;
      }
    }
    return new StubPath(sb.toString());
  }

  static class SimpleGlobSet {
    final List<Glob> globs = Lists.newArrayList();
    void add(Glob g) { globs.add(g); }
    boolean remove(Glob g) { return globs.remove(g); }
    List<Glob> matching(Path p) {
      String pathStr = p.toString();
      ImmutableList.Builder<Glob> b = ImmutableList.builder();
      for (Glob g : globs) {
        if (g.match(pathStr)) { b.add(g); }
      }
      return b.build();
    }
  }
}

final class StubPath extends Path {
  private final String s;

  StubPath(String s) { this.s = s; }
  StubPath(String s, Path[] parts) { this(s); this.parts = parts; }

  @Override public void checkAccess(AccessMode... arg0) {
    throw new UnsupportedOperationException();
  }
  @Override public int compareTo(Path o) {
    return s.compareTo(((StubPath) o).s);
  }
  @Override
  public Path copyTo(Path arg0, CopyOption... arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path createDirectory(FileAttribute<?>... arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path createFile(FileAttribute<?>... arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path createLink(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path createSymbolicLink(Path arg0, FileAttribute<?>... arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }
  @Override
  public void deleteIfExists() {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean endsWith(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean equals(Object o) {
    return o instanceof StubPath && s.equals(((StubPath) o).s);
  }
  @Override
  public boolean exists() {
    throw new UnsupportedOperationException();
  }
  @Override
  public FileStore getFileStore() {
    throw new UnsupportedOperationException();
  }
  @Override
  public FileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path getName() {
    return getName(getNameCount() - 1);
  }
  @Override
  public Path getName(int i) {
    return getParts()[i];
  }
  @Override
  public int getNameCount() {
    return getParts().length;
  }
  private Path[] parts;
  private Path[] getParts() {
    if (parts == null) {
      String[] pathParts = s.split("/");
      parts = new Path[pathParts.length];
      for (int i = parts.length; --i >= 0;) {
        Path[] partParts = new Path[1];
        partParts[0] = parts[i] = new StubPath(pathParts[i], partParts);
      }
    }
    return parts;
  }
  @Override
  public Path getParent() {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path getRoot() {
    throw new UnsupportedOperationException();
  }
  @Override
  public int hashCode() {
    return s.hashCode();
  }
  @Override
  public boolean isAbsolute() {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean isHidden() {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean isSameFile(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Iterator<Path> iterator() {
    return new Iterator<Path>() {
      private int i = 0, n = getNameCount();
      public boolean hasNext() { return i < n; }
      public Path next() { return getName(i++); }
      public void remove() { throw new UnsupportedOperationException(); }
    };
  }
  @Override
  public Path moveTo(Path arg0, CopyOption... arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public SeekableByteChannel newByteChannel(OpenOption... arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public SeekableByteChannel newByteChannel(Set<? extends OpenOption> arg0,
      FileAttribute<?>... arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public DirectoryStream<Path> newDirectoryStream() {
    throw new UnsupportedOperationException();
  }
  @Override
  public DirectoryStream<Path> newDirectoryStream(String arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public DirectoryStream<Path> newDirectoryStream(Filter<? super Path> arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public OutputStream newOutputStream(OpenOption... arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path normalize() {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean notExists() {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path readSymbolicLink() {
    throw new UnsupportedOperationException();
  }
  @Override
  public WatchKey register(WatchService arg0, Kind<?>... arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public WatchKey register(
      WatchService arg0, Kind<?>[] arg1, Modifier... arg2) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path relativize(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path resolve(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path resolve(String arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public boolean startsWith(Path arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path subpath(int arg0, int arg1) {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path toAbsolutePath() {
    throw new UnsupportedOperationException();
  }
  @Override
  public Path toRealPath(boolean arg0) {
    throw new UnsupportedOperationException();
  }
  @Override
  public String toString() { return s; }
  @Override
  public URI toUri() {
    throw new UnsupportedOperationException();
  }
  public Object getAttribute(String arg0, LinkOption... arg1) {
    throw new UnsupportedOperationException();
  }
  public <V extends FileAttributeView> V getFileAttributeView(
      Class<V> arg0, LinkOption... arg1) {
    throw new UnsupportedOperationException();
  }
  public InputStream newInputStream(OpenOption... arg0) {
    throw new UnsupportedOperationException();
  }
  public Map<String, ?> readAttributes(String arg0, LinkOption... arg1) {
    throw new UnsupportedOperationException();
  }
  public void setAttribute(String arg0, Object arg1, LinkOption... arg2) {
    throw new UnsupportedOperationException();
  }
}
