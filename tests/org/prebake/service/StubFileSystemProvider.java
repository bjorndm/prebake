package org.prebake.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class StubFileSystemProvider extends FileSystemProvider {
  final Map<String, MemFileSystem> cwdToFs = Collections.synchronizedMap(
      new HashMap<String, MemFileSystem>());
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
  final String path;
  final String[] parts;

  MemPath(MemFileSystem fs, String path) {
    assert path != null;
    this.fs = fs;
    this.path = path;
    this.parts = path.replace("([^/])/+$", "$1").split("/+");
  }

  @Override
  public void checkAccess(AccessMode... modes) throws IOException {
    fs.check(this, modes);
  }

  @Override
  public int compareTo(Path other) {
    return path.compareTo(((MemPath) other).path);
  }

  @Override
  public Path copyTo(Path target, CopyOption... options) throws IOException {
    throw new IOException();
  }

  @Override
  public Path createDirectory(FileAttribute<?>... attrs) throws IOException {
    fs.mkdir(toAbsolutePath());
    return this;
  }

  @Override
  public Path createFile(FileAttribute<?>... attrs) throws IOException {
    fs.createFile(toAbsolutePath());
    return this;
  }

  @Override
  public Path createLink(Path existing) throws IOException {
    throw new IOException();
  }

  @Override
  public Path createSymbolicLink(Path target, FileAttribute<?>... attrs)
      throws IOException {
    throw new IOException();
  }

  @Override
  public void delete() throws IOException {
    fs.delete(this);
  }

  @Override
  public void deleteIfExists() throws IOException {
    if (!this.notExists()) { delete(); }
  }

  @Override
  public boolean endsWith(Path other) {
    int m = other.getNameCount(), n = getNameCount();
    if (n < m) { return false; }
    return subpath(n - m, n).equals(other);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MemPath)) { return false; }
    MemPath that = (MemPath) other;
    return this.fs == that.fs && this.path.equals(that.path);
  }

  @Override
  public boolean exists() {
    return fs.exists(this);
  }

  @Override
  public FileStore getFileStore() throws IOException {
    throw new IOException();
  }

  @Override
  public FileSystem getFileSystem() { return fs; }

  @Override
  public Path getName() {
    int n = getNameCount();
    return n == 0 ? new MemPath(fs, "") : getName(getNameCount() - 1);
  }

  @Override
  public Path getName(int index) {
    return new MemPath(fs, parts[index]);
  }

  @Override
  public int getNameCount() {
    return parts.length;
  }

  @Override
  public MemPath getParent() {
    int n = getNameCount();
    if (n == 0) { return null; }
    return subpath(0, n - 1);
  }

  @Override
  public Path getRoot() {
    return fs.getPath("/");
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public boolean isAbsolute() {
    return path.startsWith("/");
  }

  @Override
  public boolean isHidden() {
    return getName().toString().startsWith(".");
  }

  @Override
  public boolean isSameFile(Path other) throws IOException {
    return this.toRealPath(true).equals(other.toRealPath(true));
  }

  @Override
  public Iterator<Path> iterator() {
    final int n = getNameCount();
    return new Iterator<Path>() {
      int i = 0;
      @Override
      public boolean hasNext() { return i < n; }
      @Override
      public Path next() { return subpath(i, ++i); }
      @Override
      public void remove() { throw new UnsupportedOperationException(); }
    };
  }

  @Override
  public Path moveTo(Path target, CopyOption... options) throws IOException {
    throw new IOException();
  }

  @Override
  public SeekableByteChannel newByteChannel(OpenOption... options)
      throws IOException {
    throw new IOException();
  }

  @Override
  public SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
      FileAttribute<?>... attrs) throws IOException {
    throw new IOException();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream() {
    return newDirectoryStream("*");
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(String glob) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream newOutputStream(OpenOption... options)
      throws IOException {
    throw new IOException();
  }

  @Override
  public MemPath normalize() {
    String path = this.path.replaceAll("/{2,}", "/");
    if (path.length() > 1) { path = path.replaceAll("/$", ""); }
    return !path.equals(this.path) ? new MemPath(fs, path) : this;
  }

  @Override
  public boolean notExists() {
    return !exists();
  }

  @Override
  public Path readSymbolicLink() throws IOException {
    throw new IOException();
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>... events)
      throws IOException {
    return register(watcher, events, new Modifier[0]);
  }

  @Override
  public WatchKey register(
      WatchService watcher, Kind<?>[] events, Modifier... modifiers)
      throws IOException {
    throw new IOException();
  }

  @Override
  public Path relativize(Path other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MemPath resolve(Path other) {
    if (other.isAbsolute()) { return (MemPath) other; }
    return new MemPath(fs, this.path + "/" + ((MemPath) other).path)
        .normalize();
  }

  @Override
  public Path resolve(String p) {
    return resolve(fs.getPath(p));
  }

  @Override
  public boolean startsWith(Path other) {
    int m = other.getNameCount(), n = getNameCount();
    if (n < m) { return false; }
    return subpath(0, m).equals(other);
  }

  @Override
  public MemPath subpath(int beginIndex, int endIndex) {
    if (beginIndex == endIndex) { return new MemPath(fs, ""); }
    if (beginIndex + 1 == endIndex) {
      return new MemPath(fs, parts[beginIndex]);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = beginIndex; i < endIndex; ++i) {
      if (i != beginIndex) { sb.append('/'); }
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
    return fs.cwd.resolve(this);
  }

  @Override
  public String toString() {
    return path;
  }

  @Override
  public URI toUri() {
    try {
      return new URI(fs.p.scheme, null, null, 0, path, null, null);
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Object getAttribute(String attribute, LinkOption... options) {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
      Class<V> type, LinkOption... options) {
    final MemFileSystem.Node node = fs.root.lookup(this);
    if (BasicFileAttributeView.class.isAssignableFrom(type)) {
      return (V) new BasicFileAttributeView() {
        @Override
        public String name() { return "basic"; }
        @Override
        public BasicFileAttributes readAttributes() {
          return new BasicFileAttributes() {
            @Override
            public FileTime creationTime() {
              return FileTime.fromMillis(0);
            }
            @Override
            public Object fileKey() {
              throw new UnsupportedOperationException();
            }
            @Override
            public boolean isDirectory() {
              return node != null && node.children != null;
            }
            @Override
            public boolean isOther() { return false; }
            @Override
            public boolean isRegularFile() {
              return node != null && node.children == null;
            }
            @Override
            public boolean isSymbolicLink() { return false; }
            @Override
            public FileTime lastAccessTime() { return FileTime.fromMillis(0); }
            @Override
            public FileTime lastModifiedTime() {
              return FileTime.fromMillis(0);
            }
            @Override
            public long size() { return 0; }
          };
        }
        @Override
        public void setTimes(
            FileTime lastModifiedTime, FileTime lastAccessTime,
            FileTime createTime)
            throws IOException {
          throw new IOException();
        }
      };
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public InputStream newInputStream(OpenOption... options) throws IOException {
    throw new IOException();
  }

  @Override
  public Map<String, ?> readAttributes(
      String attributes, LinkOption... options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttribute(
      String attribute, Object value, LinkOption... options) {
    throw new UnsupportedOperationException();
  }
}

class MemFileSystem extends FileSystem {
  final StubFileSystemProvider p;
  final MemPath cwd;
  Node root;

  static class Node {
    final Node parent;
    final String name;
    final Map<String, Node> children;

    Node(Node parent, String name, boolean isDir) {
      this.parent = parent;
      this.name = name;
      this.children = isDir ? new LinkedHashMap<String, Node>() : null;
      if (isDir) {
        children.put(".", this);
        if (parent != null) { children.put("..", parent); }
      }
      if (parent != null) { parent.children.put(name, this); }
    }

    @Override
    public String toString() {
      return parent == null ? name : parent.toString() + "/" + name;
    }

    Node lookup(Iterable<Path> paths) {
      Node n = this;
      for (Path p : paths) {
        if (n == null || n.children == null) { return null; }
        String s = p.toString();
        if ("".equals(s)) { continue; }
        n = n.children.get(s);
      }
      return n;
    }
  }

  MemFileSystem(StubFileSystemProvider p, String cwd) {
    this.p = p;
    this.cwd = new MemPath(this, cwd);
    root = new Node(null, "", true);
  }

  void delete(MemPath p) throws IOException {
    Node node = root.lookup(p);
    if (node == null || node.parent == null) {
      throw new IOException(p.toString());
    }
    Node removed = node.parent.children.remove(node.name);
    assert removed == node;
  }

  boolean exists(MemPath p) {
    return root.lookup(p) != null;
  }

  /** @param modes unused. */
  void check(MemPath path, AccessMode... modes) throws IOException {
    Node node = root.lookup(path);
    if (node == null) { throw new IOException(path.toString()); }
  }

  void mkdir(MemPath path) throws IOException {
    Node n = root;
    for (Path p : path) {
      if (n.children == null) { throw new IOException(); }
      String s = p.toString();
      if ("".equals(s)) { continue; }
      Node child = n.children.get(s);
      if (child == null) { child = new Node(n, s, true); }
      n = child;
    }
  }

  void createFile(MemPath path) throws IOException {
    String s = path.getName().toString();
    if ("".equals(s)) { throw new IOException(path.toString()); }
    MemPath parent = path.getParent();
    Node parentNode = parent == null ? root : root.lookup(parent);
    if (parentNode == null || parentNode.children == null) {
      throw new IOException(path.toString());
    }
    Node child = parentNode.children.get(s);
    if (child == null) {
      child = new Node(parentNode, s, false);
    } else if (child.children != null) {
      throw new IOException(path.toString());
    }
  }

  @Override
  public void close() { root = null; }

  @Override
  public Iterable<FileStore> getFileStores() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MemPath getPath(String path) { return new MemPath(this, path); }

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
      @Override
      public boolean matches(Path path) {
        return path instanceof MemPath
            && p.matcher(((MemPath) path).path).matches();
      }
    };
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    if (root == null) { return Collections.emptySet(); }
    return Collections.singleton((Path) getPath(root.toString()));
  }

  @Override
  public String getSeparator() { return "/"; }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isOpen() { return root != null; }

  @Override
  public boolean isReadOnly() { return false; }

  @Override
  public WatchService newWatchService() throws IOException {
    throw new IOException();
  }

  @Override
  public FileSystemProvider provider() {
    return p;
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return Collections.singleton("basic");
  }
}
