package org.prebake.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKind;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class StubFileSystemProvider extends FileSystemProvider {
  final Map<String, MemFileSystem> cwdToFs = Collections.synchronizedMap(
      Maps.<String, MemFileSystem>newHashMap());
  final String scheme;

  public StubFileSystemProvider(String scheme) { this.scheme = scheme; }

  @Override
  public MemFileSystem getFileSystem(URI uri) {
    return newFileSystem(uri, Collections.<String, String>emptyMap());
  }

  @Override
  public Path getPath(URI uri) {
    return new MemPath(getFileSystem(uri), uri.getPath());
  }

  @Override
  public String getScheme() {
    return scheme;
  }

  @Override
  public MemFileSystem newFileSystem(URI uri, Map<String, ?> env) {
    String cwd = uri.getFragment();
    assert cwd != null;
    synchronized (cwdToFs) {
      MemFileSystem fs = cwdToFs.get(cwd);
      if (fs == null) {
        fs = new MemFileSystem(this, cwd);
        cwdToFs.put(cwd, fs);
      }
      return fs;
    }
  }
}

class MemPath extends Path {
  final MemFileSystem fs;
  final String[] parts;
  final boolean isAbs;

  MemPath(MemFileSystem fs, String path) {
    String sep = fs.getSeparator();
    this.fs = fs;
    this.isAbs = path.startsWith(sep);
    String[] parts = path.split(Pattern.quote(sep) + "+");
    if (isAbs && parts.length != 0) {
      String[] newParts = new String[parts.length - 1];
      System.arraycopy(parts, 1, newParts, 0, parts.length - 1);
      parts = newParts;
    }
    this.parts = parts;
  }

  @Override
  public FileSystem getFileSystem() { return fs; }

  @Override
  public void checkAccess(AccessMode... modes) throws IOException {
    if (!fs.__hasAccess(this, modes)) {
      throw new IOException();
    }
  }

  @Override
  public boolean exists() {
    try {
      checkAccess();
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  @Override
  public boolean notExists() { return !exists(); }

  @Override
  public void delete() throws IOException {
    fs.__delete(this);
  }

  @Override
  public int compareTo(Path other) {
    MemPath that = (MemPath) other;
    int delta = (isAbs ? 1 : 0) - (that.isAbs ? 1 : 0);
    if (delta != 0) { return delta; }
    int n = Math.min(parts.length, that.parts.length);
    for (int i = 0; i < n; ++i) {
      delta = parts[i].compareTo(that.parts[i]);
      if (delta != 0) { return delta; }
    }
    return parts.length - that.parts.length;
  }

  @Override
  public Path createDirectory(FileAttribute<?>... attrs) throws IOException {
    fs.__mkdir(this);
    return this;
  }

  @Override
  public Path createFile(FileAttribute<?>... attrs) throws IOException {
    fs.__touch(this);
    return this;
  }

  @Override
  public OutputStream newOutputStream(OpenOption...openOptions)
      throws IOException {
    return fs.__write(this, openOptions);
  }

  public InputStream newInputStream(OpenOption...openOptions)
      throws IOException {
    return fs.__read(this, openOptions);
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream() throws IOException {
    return newDirectoryStream((Filter<Path>) null);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Path)) { return false; }
    MemPath that = (MemPath) other;
    return isAbs == that.isAbs && Arrays.equals(parts, that.parts);
  }

  @Override
  public int hashCode() {
    return (isAbs ? 1 : 0) + Arrays.hashCode(parts);
  }

  @Override
  public Path getName() {
    return parts.length == 0 ? this : new MemPath(fs, parts[parts.length - 1]);
  }

  @Override
  public Path getName(int i) {
    return new MemPath(fs, parts[i]);
  }

  @Override
  public int getNameCount() { return parts.length; }

  @Override
  public boolean isAbsolute() { return isAbs; }

  @Override
  public Path relativize(Path p) {
    Path base = this.toRealPath(false);
    p = ((MemPath) p).toRealPath(false);
    int n = Math.min(p.getNameCount(), base.getNameCount());
    int nCommon = 0;
    while (nCommon < n && p.getName(nCommon).equals(base.getName(nCommon))) {
      ++nCommon;
    }
    Path dots = p.getFileSystem().getPath("..");
    Path relBase = null;
    for (int i = nCommon, end = base.getNameCount(); i < end; ++i) {
      if (relBase == null) {
        relBase = dots;
      } else {
        relBase = relBase.resolve(dots);
      }
    }
    Path diffPart = p.subpath(nCommon, p.getNameCount());
    return relBase == null ? diffPart : relBase.resolve(diffPart);
  }

  @Override
  public MemPath subpath(int start, int end) {
    StringBuilder sb = new StringBuilder();
    if (isAbs && start == 0 && end != 0) { sb.append(fs.getSeparator()); }
    for (int i = start; i < end; ++i) {
      String part = parts[i];
      if ("".equals(part)) { continue; }
      if (sb.length() != 0) { sb.append(fs.getSeparator()); }
      sb.append(parts[i]);
    }
    return new MemPath(fs, sb.toString());
  }

  @Override
  public MemPath toAbsolutePath() {
    return toRealPath(false);
  }

  @Override
  public MemPath toRealPath(boolean resolveLinks) {
    return fs.__toRealPath(this);
  }

  @Override
  public MemPath resolve(Path other) {
    MemPath that = (MemPath) other;
    if (that.isAbs) { return that; }
    return new MemPath(fs, this + fs.getSeparator() + that);
  }

  @Override
  public MemPath resolve(String other) {
    return resolve(getFileSystem().getPath(other));
  }

  @Override
  public boolean startsWith(Path path) {
    MemPath p = (MemPath) path;
    if (this.isAbs != p.isAbs) { return false; }
    int n = p.parts.length;
    if (parts.length < n) { return false; }
    return Arrays.asList(p.parts).equals(Arrays.asList(parts).subList(0, n));
  }

  @Override
  public URI toUri() {
    return fs.__toUri(this);
  }

  @Override
  public String toString() {
    int n = parts.length;
    String sep = fs.getSeparator();
    switch (n) {
      case 0: return isAbs ? sep : "";
      case 1: return isAbs ? sep + parts[0] : parts[0];
      default:
        StringBuilder sb = new StringBuilder();
      if (isAbs) { sb.append(sep); }
      if (n != 0) {
        sb.append(parts[0]);
        for (int i = 1; i < n; ++i) {
          sb.append(sep).append(parts[i]);
        }
      }
      return sb.toString();
    }
  }

  @Override
  public WatchKey register(WatchService ws, WatchEvent.Kind<?>... kinds)
      throws IOException {
    return fs.__register(this, ws, kinds);
  }

  public Object getAttribute(String attribute, LinkOption... options)
      throws IOException {
    Node n = fs.lookup(this);
    if (n == null) { throw new FileNotFoundException(toString()); }
    if ("size".equals(attribute)) {
      return n.isDir() ? 0L : Long.valueOf(n.content.size());
    } else if ("isRegularFile".equals(attribute)) {
      return n.isDir() ? Boolean.FALSE : Boolean.TRUE;
    } else if ("isDirectory".equals(attribute)) {
      return n.isDir() ? Boolean.TRUE : Boolean.FALSE;
    } else if ("isSymbolicLink".equals(attribute)) {
      return Boolean.FALSE;
    } else if ("isOther".equals(attribute)) {
      return Boolean.FALSE;
    } else {
      // TODO fileKey, timestamps
      throw new IllegalArgumentException(attribute);
    }
  }

  @Override
  public Path copyTo(Path target, CopyOption... options) throws IOException {
    Node a = fs.lookup(this);
    Node b = fs.lookup((MemPath) target);
    if (a == b) { return target; }
    if (a == null) { throw new IOException(); }
    if (a.isDir()) { throw new IOException(); }
    if (b == null) {
      target.createFile();
      b = fs.lookup((MemPath) target);
    }
    if (b.isDir()) { throw new IOException(); }
    Node a2 = new Node(b.getName(), b.getParent(), a.isDir());
    a2.content.write(a.content.toByteArray());
    return target;
  }

  @Override
  public Path createLink(Path existing) throws IOException {
    throw new IOException(existing.toString());
  }

  @Override
  public Path createSymbolicLink(Path target, FileAttribute<?>... attrs)
      throws IOException {
    throw new IOException(target.toString());
  }

  @Override
  public void deleteIfExists() throws IOException {
    if (exists()) { delete(); }
  }

  @Override
  public boolean endsWith(Path other) {
    MemPath p = (MemPath) other;
    int n = p.parts.length;
    int m = parts.length;
    if (m < n) { return false; }
    return Arrays.asList(p.parts).equals(
        Arrays.asList(parts).subList(m - n, m));
  }

  @Override
  public FileStore getFileStore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getParent() {
    if (parts.length == 0 || parts.length == 1 && "".equals(parts[0])) {
      return null;
    }
    return normalize().subpath(0, Math.max(0, getNameCount() - 1));
  }

  @Override
  public Path getRoot() { return fs.getRootDirectories().iterator().next(); }

  @Override
  public boolean isHidden() {
    return getName().toString().startsWith(".");
  }

  @Override
  public boolean isSameFile(Path other) {
    return this.fs == other.getFileSystem()
        && this.toAbsolutePath().equals(((MemPath) other).toAbsolutePath());
  }

  @Override
  public final Iterator<Path> iterator() {
    return new Iterator<Path>() {
      int i = 0;

      public boolean hasNext() { return i < getNameCount(); }
      public Path next() { return getName(i++); }
      public void remove() { throw new UnsupportedOperationException(); }
    };
  }

  @Override
  public Path moveTo(Path target, CopyOption... options) throws IOException {
    Node a = fs.lookup(this);
    Node b = fs.lookup((MemPath) target);
    if (a == b) { return target; }
    if (a == null) { throw new IOException(); }
    if (b == null) {
      if (a.isDir()) {
        target.createDirectory();
      } else {
        target.createFile();
      }
      b = fs.lookup((MemPath) target);
    }
    if (a.isDir() != b.isDir()) { throw new IOException(); }
    a.delete();
    a.rename(b.getName());
    a.reparent(b.getParent());
    return target;
  }

  @Override
  public SeekableByteChannel newByteChannel(OpenOption... options) {
    return newByteChannel(ImmutableSet.of(options), new FileAttribute<?>[0]);
  }

  @Override
  public SeekableByteChannel newByteChannel(
      Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(String glob)
      throws IOException {
    final PathMatcher matcher = fs.getPathMatcher("glob:" + glob);
    return newDirectoryStream(new DirectoryStream.Filter<Path>() {
      public boolean accept(Path entry) { return matcher.matches(entry); }
    });
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter)
      throws IOException {
    return fs.__children(this, filter);
  }

  @Override
  public Path normalize() {
    List<String> norm = Lists.newArrayList();
    for (String part : parts) {
      if (".".equals(part)) { continue; }
      if ("..".equals(part)) {
        int last = norm.size() - 1;
        if (last >= 0 && !"..".equals(norm.get(last))) {
          norm.remove(last);
          continue;
        }
        if (isAbs) { continue; }
      }
      norm.add(part);
    }
    StringBuilder sb = new StringBuilder();
    if (isAbs) { sb.append('/'); }
    String sep = "";
    for (String p : norm) {
      sb.append(sep).append(p);
      sep = "/";
    }
    return new MemPath(fs, sb.toString());
  }

  @Override
  public Path readSymbolicLink() throws IOException {
    throw new IOException();
  }

  @Override
  public WatchKey register(
      WatchService watcher, Kind<?>[] events, Modifier... modifiers)
      throws IOException {
    // TODO: modifiers
    return register(watcher, events);
  }

  public <V extends FileAttributeView> V getFileAttributeView(
      Class<V> type, final LinkOption... options) {
    if (!BasicFileAttributeView.class.equals(type)) {
      throw new IllegalArgumentException();
    }
    return type.cast(new BasicFileAttributeView() {

      public String name() { return getName().toString(); }

      public BasicFileAttributes readAttributes() throws IOException {
        final Map<String, ?> attrs = MemPath.this.readAttributes("*", options);
        return new BasicFileAttributes() {
          public FileTime creationTime() {
            return (FileTime) attrs.get("creationTime");
          }
          public Object fileKey() {
            return attrs.get("fileKey");
          }
          public boolean isDirectory() {
            return (Boolean) attrs.get("isDirectory");
          }
          public boolean isOther() {
            return (Boolean) attrs.get("isOther");
          }
          public boolean isRegularFile() {
            return (Boolean) attrs.get("isRegularFile");
          }
          public boolean isSymbolicLink() {
            return (Boolean) attrs.get("isSymbolicLink");
          }
          public FileTime lastAccessTime() {
            return (FileTime) attrs.get("lastAccessTime");
          }
          public FileTime lastModifiedTime() {
            return (FileTime) attrs.get("lastModifiedTime");
          }
          public long size() { return (Long) attrs.get("size"); }
        };
      }

      public void setTimes(
          FileTime lastModifiedTime, FileTime lastAccessTime,
          FileTime createTime) {
        MemPath.this.setAttribute("lastModifiedTime", lastModifiedTime);
        MemPath.this.setAttribute("lastAccessTime", lastModifiedTime);
        MemPath.this.setAttribute("createTime", lastModifiedTime);
      }
    });
  }

  public Map<String, ?> readAttributes(String attributes, LinkOption... options)
      throws IOException {
    ImmutableMap.Builder<String, Object> attrs = ImmutableMap.builder();
    for (String attr : attributes.split(",")) {
      if ("*".equals(attr)) {
        return readAttributes(
            "size,isRegularFile,isDirectory,isSymbolicLink,isOther", options);
      }
      attrs.put(attr, getAttribute(attr, options));
    }
    return attrs.build();
  }

  public void setAttribute(
      String attribute, Object value, LinkOption... options) {
    throw new UnsupportedOperationException();
  }
}

class MemFileSystem extends FileSystem {
  private Node root = new Node("", null, true);
  private final StubFileSystemProvider p;
  private final MemPath cwd;

  MemFileSystem(StubFileSystemProvider p, String cwd) {
    this.p = p;
    this.cwd = getPath(cwd);
    try {
      __mkdir(this.cwd);
    } catch (IOException ex) {
      throw new IOError(ex);
    }
  }

  @Override
  public FileSystemProvider provider() { return p; }

  void __mkdir(Path dir) throws IOException {
    dir = dir.toAbsolutePath();
    Node n = root;
    for (Path part : dir) {
      String name = part.toString();
      if ("".equals(name)) { continue; }
      Node child = n.getChild(name);
      if (child == null) {
        child = new Node(name, n, true);
      } else if (!child.isDir()) {
        throw new IOException();
      }
      n = child;
    }
  }

  void __touch(MemPath file) throws IOException {
    file = file.toAbsolutePath();
    Node parent = root;
    Node n = root;
    String name = null;
    for (Path part : file) {
      String partName = part.toString();
      if ("".equals(partName)) { continue; }
      if (n == null) {
        System.err.println("file=" + file);
        System.err.println(root.toTree(0, new StringBuilder()));
        throw new IOException(); }
      if (!n.isDir()) { throw new IOException(); }
      Node child = n.getChild(partName);
      parent = n;
      n = child;
      name = partName;
    }
    if (name != null) {
      if (n == null) {
        n = new Node(name, parent, false);
      }
    }
  }

  Node lookup(MemPath p) {
    assert p.fs == this;
    p = __toRealPath(p);
    Node n = root;
    for (Path part : p) {
      Node child = n.getChild(part.toString());
      if (child == null) { return null; }
      n = child;
    }
    return n;
  }

  /** @param modes TODO ignored */
  boolean __hasAccess(MemPath p, AccessMode... modes) {
    Node node = lookup(p);
    return node != null;
  }

  boolean __isDirectory(MemPath p) {
    Node node = lookup(p);
    return node != null && node.isDir();
  }

  boolean __isRegularFile(MemPath p) {
    Node node = lookup(p);
    return node != null && !node.isDir();
  }

  MemPath __toRealPath(MemPath p) {
    assert p.fs == this;
    if (p.isAbs) { return p; }
    return new MemPath(this, cwd + "/" + p);
  }

  URI __toUri(MemPath p) {
    return URI.create("file://" + __toRealPath(p));
  }

  @Override
  public MemPath getPath(String path) throws InvalidPathException {
    return new MemPath(this, path);
  }

  @Override
  public String getSeparator() {
    return "/";
  }

  @Override
  public boolean isOpen() {
    return root != null;
  }

  @Override
  public void close() {
    root = null;
  }

  @Override
  public WatchService newWatchService() {
    return new StubWatchService(this);
  }

  InputStream __read(final MemPath p, OpenOption... opts) throws IOException {
    final EnumSet<StandardOpenOption> options = EnumSet.noneOf(
        StandardOpenOption.class);
    for (OpenOption opt : opts) { options.add((StandardOpenOption) opt); }
    if (options.contains(StandardOpenOption.WRITE)
        || options.contains(StandardOpenOption.CREATE)
        || options.contains(StandardOpenOption.CREATE_NEW)
        || options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
      throw new IllegalArgumentException();
    }
    final Node node = lookup(p);
    if (node == null) { throw new FileNotFoundException(p.toString()); }
    if (node.isDir()) { throw new IOException(); }
    synchronized (node) {
      if (!node.forRead()) { throw new IOException("file clash"); }
      return new FilterInputStream(
          new ByteArrayInputStream(node.content.toByteArray())) {
        boolean closed = false;
        @Override
        public void close() throws IOException {
          synchronized (this) {
            if (closed) { return; }
            closed = true;
          }
          node.releaseRead();
          super.close();
          if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
            p.delete();
          }
        }
      };
    }
  }

  OutputStream __write(final MemPath p, OpenOption... opts) throws IOException {
    final EnumSet<StandardOpenOption> options = EnumSet.noneOf(
        StandardOpenOption.class);
    for (OpenOption opt : opts) { options.add((StandardOpenOption) opt); }
    if (options.contains(StandardOpenOption.READ)) {
      throw new IllegalArgumentException();
    }
    Node n = lookup(p);
    if (n == null) {
      if (options.contains(StandardOpenOption.CREATE)
          || options.contains(StandardOpenOption.CREATE_NEW)) {
        Node parent = lookup(p.subpath(0, p.getNameCount() - 1));
        if (parent == null || !parent.isDir()) {
          throw new IOException(p.toString());
        }
        n = new Node(p.getName().toString(), parent, false);
      } else {
        throw new IOException(p.toString());
      }
    } else if (options.contains(StandardOpenOption.CREATE_NEW)) {
      throw new IOException(p.toString());
    } else if (n.isDir()) {
      throw new IOException(p.toString());
    }
    final Node node = n;
    synchronized (node) {
      if (!node.forWrite()) { throw new IOException("file clash"); }
      if (options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
        node.content.reset();
      }
      return new FilterOutputStream(node.content) {
        boolean closed = false;
        @Override
        public void close() throws IOException {
          synchronized (this) {
            if (closed) { return; }
            closed = true;
          }
          node.releaseWrite();
          super.close();
          if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
            p.delete();
          }
        }
      };
    }
  }

  void __delete(MemPath p) throws IOException {
    Node n = lookup(p);
    if (n == null) { throw new FileNotFoundException(); }
    if (n.getParent() == null) { throw new IOException(); }
    n.delete();
  }

  /** @param kinds TODO */
  WatchKey __register(MemPath p, WatchService ws, Kind<?>... kinds)
      throws IOException {
    Node n = lookup(p);
    if (n.isDir()) {
      StubWatchKey key = new StubWatchKey(n, (StubWatchService) ws);
      n.watchers.add(key);
      return key;
    } else {
      throw new IOException();
    }
  }

  DirectoryStream<Path> __children(
      final MemPath p, final DirectoryStream.Filter<? super Path> filter)
      throws IOException {
    final Node n = lookup(p);
    if (n == null || !n.isDir()) { throw new IOException(); }
    return new DirectoryStream<Path>() {
      Node node = n;

      public void close() { node = null; }

      public Iterator<Path> iterator() {
        if (node == null) { throw new IllegalStateException(); }
        final Iterator<String> it = ImmutableList.copyOf(
            node.getChildren().keySet()).iterator();
        return new Iterator<Path>() {
          Path pending = null;

          public boolean hasNext() {
            fetch();
            return pending != null;
          }

          public Path next() {
            fetch();
            Path p = pending;
            if (p == null) { throw new NoSuchElementException(); }
            pending = null;
            return p;
          }

          public void remove() { throw new UnsupportedOperationException(); }

          private void fetch() {
            if (pending != null) { return; }
            if (node != null) {
              while (it.hasNext()) {
                String name = it.next();
                Node child = node.getChild(name);
                if (child != null) {
                  Path p = child.toPath(MemFileSystem.this);
                  try {
                    if (filter == null || filter.accept(p)) {
                      pending = p;
                      return;
                    }
                  } catch (IOException ex) {
                    ConcurrentModificationException cme
                        = new ConcurrentModificationException();
                    cme.initCause(ex);
                    throw cme;
                  }
                }
              }
              node = null;
            }
          }
        };
      }
    };
  }

  @Override
  public boolean isReadOnly() { return false; }

  @Override
  public Iterable<FileStore> getFileStores() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    int colon = syntaxAndPattern.indexOf(':');
    String syntax = syntaxAndPattern.substring(0, colon);
    String pattern = syntaxAndPattern.substring(colon + 1);
    if ("glob".equals(syntax)) {
      pattern = pattern.replaceAll("[.\\\\]", "\\\\$1")
          .replaceAll("**", ".*")
          .replaceAll("*", "[^/]*")
          .replaceAll("?", ".");
    }
    final Pattern p = Pattern.compile(pattern);
    return new PathMatcher() {
      public boolean matches(Path path) {
        return path instanceof MemPath && p.matcher(path.toString()).matches();
      }
    };
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    if (null == root) { return Collections.<Path>emptyList(); }
    return Collections.<Path>singletonList(getPath("/"));
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return Collections.singleton("basic");
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException();
  }
}

class StubWatchService extends WatchService {
  BlockingQueue<WatchKey> q = new LinkedBlockingQueue<WatchKey>();
  final MemFileSystem fs;

  StubWatchService(MemFileSystem fs) { this.fs = fs; }

  @Override
  public void close() {
    q.clear();
    q = null;
  }

  @Override
  public WatchKey poll() {
    return q.poll();
  }

  @Override
  public WatchKey take() throws InterruptedException {
    return q.take();
  }

  boolean isClosed() { return q == null; }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit)
      throws InterruptedException {
    return q.poll(timeout, unit);
  }
}

class StubWatchKey extends WatchKey {
  final Node n;
  final StubWatchService ws;
  final Map<String, Set<WatchEvent.Kind<Path>>> events
      = Maps.newLinkedHashMap();
  boolean valid = true;

  StubWatchKey(Node n, StubWatchService ws) {
    this.n = n;
    this.ws = ws;
  }

  @Override
  public void cancel() {
    n.watchers.remove(this);
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  @Override
  public List<WatchEvent<?>> pollEvents() {
    ImmutableList.Builder<WatchEvent<?>> eventList = ImmutableList.builder();
    synchronized (events) {
      for (Map.Entry<String,Set<WatchEvent.Kind<Path>>> e : events.entrySet()) {
        final String name = e.getKey();
        for (final WatchEvent.Kind<Path> k : e.getValue()) {
          eventList.add(new WatchEvent<Path>() {
            @Override
            public Path context() { return ws.fs.getPath(name); }

            @Override
            public int count() { return 1; }

            @Override
            public WatchEvent.Kind<Path> kind() { return k; }
          });
        }
      }
    }
    return eventList.build();
  }

  @Override
  public boolean reset() {
    synchronized (events) {
      events.clear();
    }
    return isValid();
  }

  synchronized boolean notify(WatchEvent.Kind<Path> k, String name) {
    BlockingQueue<WatchKey> q = ws.q;
    if (q == null) { return false; }
    synchronized (events) {
      boolean enqueue =  events.isEmpty();
      Set<WatchEvent.Kind<Path>> kinds = events.get(name);
      if (kinds == null) {
        events.put(name, kinds = Sets.newLinkedHashSet());
      }
      kinds.add(k);
      if (enqueue) {
        try {
          q.put(this);
        } catch (InterruptedException ex) {
          // Queue closed.  Nothing to do here.
        }
      }
    }
    return true;
  }
}

final class Node {
  private String name;
  private Node parent;
  private final Map<String, Node> children;
  final ByteArrayOutputStream content;
  final List<StubWatchKey> watchers;
  private int openCount;

  Node(String name, Node parent, boolean isDir) {
    this.name = name;
    if (isDir) {
      children = Maps.newLinkedHashMap();
      content = null;
      watchers = Lists.newArrayList();
    } else {
      children = null;
      content = new ByteArrayOutputStream();
      watchers = null;
    }
    if (parent != null) {
      reparent(parent);
    }
  }

  void delete() {
    reparent(null);
  }

  void reparent(Node newParent) {
    if (parent == newParent) { return; }
    if (parent != null) {
      parent.children.remove(name);
      parent.bcast(StandardWatchEventKind.ENTRY_DELETE, name);
      parent = null;
      invalidate();
    }
    if (newParent != null) {
      Node old = newParent.children.put(name, this);
      parent = newParent;
      parent.bcast(StandardWatchEventKind.ENTRY_CREATE, name);
      if (old != null) { old.reparent(null); }
    }
  }

  private void invalidate() {
    if (!isDir()) { return; }
    for (StubWatchKey k : watchers) { k.valid = false; }
    watchers.clear();
    for (Node c : children.values()) { c.invalidate(); }
  }

  Node getParent() { return parent; }
  Node getChild(String name) {
    if (".".equals(name)) { return this; }
    if ("..".equals(name)) { return parent; }
    return children.get(name);
  }
  Map<String, Node> getChildren() {
    return Collections.unmodifiableMap(children);
  }
  String getName() { return name; }

  void rename(String newName) {
    if (name.equals(newName)) { return; }
    Node parent = this.parent;
    delete();
    this.name = newName;
    reparent(parent);
  }

  StringBuilder toTree(int depth, StringBuilder out) {
    for (int i = depth; --i >= 0;) { out.append("  "); }
    out.append(name);
    if (isDir()) {
      out.append("/");
      for (Node n : children.values()) {
        out.append("\n");
        n.toTree(depth + 1, out);
      }
    }
    return out;
  }

  synchronized void releaseWrite() {
    if (openCount != -1) { throw new IllegalStateException(); }
    openCount = 0;
    parent.bcast(StandardWatchEventKind.ENTRY_CREATE, name);
  }
  synchronized void releaseRead() {
    if (openCount <= 0) { throw new IllegalStateException(); }
    --openCount;
  }
  synchronized boolean forRead() {
    if (openCount >= 0) {
      ++openCount;
      return true;
    }
    return false;
  }
  synchronized boolean forWrite() {
    if (openCount == 0) {
      openCount = -1;
      return true;
    }
    return false;
  }

  synchronized void bcast(WatchEvent.Kind<Path> kind, String name) {
    Iterator<StubWatchKey> it = watchers.iterator();
    while (it.hasNext()) {
      if (!it.next().notify(kind, name)) { it.remove(); }
    }
  }

  @Override
  public String toString() {
    if (parent == null) { return name; }
    return parent + "/" + name;
  }

  Path toPath(MemFileSystem fs) {
    return fs.getPath("/" + toString());
  }

  boolean isDir() { return children != null; }
}
