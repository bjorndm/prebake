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

package org.prebake.util;

import org.prebake.core.Glob;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.fs.FilePerms;
import org.prebake.js.CommonEnvironment;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;

import com.google.caja.lexer.escaping.UriUtil;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import org.junit.After;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public abstract class PbTestCase extends Assert {
  private Logger logger;
  private Handler handler;
  private List<String> log;

  @After
  public void cleanupLogger() {
    if (logger != null) {
      logger.removeHandler(handler);
      logger = null;
      handler = null;
      log = null;
    }
  }

  public String getName() { return getClass().getSimpleName(); }

  protected List<String> getLog() { return log; }

  protected Logger getLogger(Level level) {
    if (logger == null) {
      log = Collections.synchronizedList(Lists.<String>newArrayList());
      logger = Logger.getLogger(getName());
      logger.addHandler(handler = new Handler() {
        @Override public void close() { log = null; }
        @Override public void flush() { /* nothing to flush */ }
        @Override
        public void publish(LogRecord r) {
          String sourceClass = r.getSourceClassName();
          String prefix = "";
          if (sourceClass.contains(".js:")) {
            // We're testing JS.
            prefix = sourceClass + ":";
          }
          log.add(prefix + r.getLevel() + ": "
                  + MessageFormat.format(r.getMessage(), r.getParameters()));
          if (r.getThrown() != null) {
            log.add(r.getThrown().toString());
          }
          if (PbTestCase.this.logger.getLevel().intValue()
              < PbTestCase.this.logger.getParent().getLevel().intValue()) {
            System.err.println(
                r.getLevel() + " : "
                + MessageFormat.format(r.getMessage(), r.getParameters()));
          }
        }
      });
    }
    logger.setLevel(level);
    return logger;
  }

  public ImmutableMap<String, ?> getCommonJsEnv(boolean dosPrefs)
      throws IOException {
    if (dosPrefs) {
      return CommonEnvironment.makeEnvironment(
          fileSystemFromAsciiArt("C:", "C:\\").getRootDirectories().iterator()
              .next(),
          ImmutableMap.of(
              "file.separator", "\\",
              "os.arch", "i386",
              "os.name", "generic-windows",
              "os.version", "7"));
    } else {
      return CommonEnvironment.makeEnvironment(
          fileSystemFromAsciiArt("/", "/").getPath("/"),
          ImmutableMap.of(
              "file.separator", "/",
              "os.arch", "i386",
              "os.name", "generic-posix",
              "os.version", "1"));
    }
  }

  public ImmutableMap<String, ?> getCommonJsEnv() throws IOException {
    return getCommonJsEnv(false);
  }

  protected static void rmDirTree(File f) {
    try {
      Files.deleteRecursively(f);
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
    }
  }

  private static final FileAttribute<?>[] DIR_ATTRS
      = FilePerms.perms(0700, true);

  protected static void mkdirs(Path p) throws IOException {
    if (!p.exists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent); }
      p.createDirectory(DIR_ATTRS);
    }
  }

  protected void writeFile(Path p, String content) throws IOException {
    OutputStream out = p.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      out.write(content.getBytes(Charsets.UTF_8));
    } finally {
      out.close();
    }
  }

  public static String fileSystemToAsciiArt(
      FileSystem fs, final int contentLimit) {
    final StringBuilder sb = new StringBuilder();
    for (Path root : fs.getRootDirectories()) {
      java.nio.file.Files.walkFileTree(root, new FileVisitor<Path>() {
        private int indent;

        public FileVisitResult postVisitDirectory(Path d, IOException ex) {
          --indent;
          return FileVisitResult.CONTINUE;
        }
        public FileVisitResult preVisitDirectory(Path d) {
          indent();
          if ("/".equals(d.getName().toString())) {
            sb.append("/\n");
          } else {
            sb.append(d.getName()).append('/').append('\n');
          }
          ++indent;
          return FileVisitResult.CONTINUE;
        }
        public FileVisitResult preVisitDirectoryFailed(Path d, IOException ex) {
          ex.printStackTrace();
          indent();
          if ("/".equals(d.getName().toString())) {
            sb.append("/ ?\n");
          } else {
            sb.append(d.getName()).append("/ ?\n");
          }
          return FileVisitResult.CONTINUE;
        }
        public FileVisitResult visitFile(Path f, BasicFileAttributes atts) {
          indent();
          String content = null;
          sb.append(f.getName());
          try {
            Reader r = new InputStreamReader(
                f.newInputStream(), Charsets.UTF_8);
            try {
              content = CharStreams.toString(r);
            } finally {
              r.close();
            }
          } catch (IOException ex) {
            ex.printStackTrace();
          }
          if (content != null && !"".equals(content)) {
            if (content.length() <= contentLimit) {
              sb.append(' ');
              JsonSink.stringify(content, sb);
            } else {
              sb.append(" \"...\"");
            }
          }
          sb.append('\n');
          return FileVisitResult.CONTINUE;
        }
        public FileVisitResult visitFileFailed(Path f, IOException ex) {
          ex.printStackTrace();
          indent();
          sb.append(f.getName()).append(" ?\n");
          return FileVisitResult.CONTINUE;
        }
        private void indent() {
          for (int i = indent; --i >= 0;) { sb.append("  "); }
        }
      });
    }
    return sb.toString();
  }

  /**
   * Creates an in-memory file system given ascii art like:<pre>
   * /
   *   foo/
   *     bar.txt 'Hello, World!'
   *   baz/ (rx)
   * </pre>
   * where directory structure is inferred based on indentation similar to
   * Python.  Names ending in <tt>/</tt> are directories.  Optional permissions
   * are inside parentheses, and optional file content is inside single quotes
   * (<tt>'</tt>).
   */
  protected FileSystem fileSystemFromAsciiArt(String cwd, String... asciiArt)
      throws IOException {
    String sep = cwd.contains("/") ? "/" : "\\";
    char sepChar = sep.charAt(0);
    int sepIndex = cwd.indexOf(sep);
    String root = cwd.substring(0, sepIndex < 0 ? cwd.length() : sepIndex);
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///?sep=" + UriUtil.encode(sep) + "&root="
                   + UriUtil.encode(root) + "#" + UriUtil.encode(cwd)));
    List<Integer> indentStack = Lists.newArrayList(-1);
    List<Path> paths = Lists.newArrayList();
    paths.add(fs.getPath(root));
    String asciiArtStr = Joiner.on('\n').join(asciiArt);
    assert asciiArtStr.indexOf('\t') < 0;
    for (String line : asciiArtStr.split("[\r\n]+")) {
      if ("".equals(line.trim())) { continue; }
      int indent;
      String fileName;
      boolean isDir = false;
      String perms = null;
      String contentEncoded = null;

      int pos = 0, n = line.length();

      while (pos < n && line.charAt(pos) == ' ') { ++pos; }
      indent = pos;
      for (; ; ++pos) {
        if (pos == n) {
          fileName = line.substring(indent);
          break;
        }
        char ch = line.charAt(pos);
        if (ch == ' ' || ch == '(' || ch == '"') {
          fileName = line.substring(indent, pos);
          break;
        } else if (ch == sepChar) {
          fileName = line.substring(indent, pos);
          isDir = true;
          ++pos;
          break;
        }
      }
      while (pos < n) {
        while (pos < n && line.charAt(pos) == ' ') { ++pos; }
        if (pos == n) { break; }
        switch (line.charAt(pos)) {
          case '(': {
            int end = pos + 1;
            while (end < n && "rwx".indexOf(line.charAt(end)) >= 0) { ++end; }
            if (end == n || line.charAt(end) != ')') {
              throw new IllegalArgumentException(
                  "Expected close parenthesis : " + line);
            }
            perms = line.substring(pos + 1, end);
            pos = end + 1;
            break;
          }
          case '"': {
            int end = pos + 1;
            while (line.charAt(end) != '"') {
              if (line.charAt(end) == '\\') { ++end; }
              ++end;
            }
            contentEncoded = line.substring(pos, end + 1);
            pos = end + 1;
            break;
          }
          default:
            throw new IllegalArgumentException(
                line.charAt(pos) + " in " + line);
        }
      }

      if (isDir && contentEncoded != null) {
        throw new IllegalArgumentException(line);
      }
      int permBits = 0;
      if (perms != null) {
        if (perms.contains("r")) { permBits |= 0400; }
        if (perms.contains("w")) { permBits |= 0200; }
        if (perms.contains("x")) { permBits |= 0100; }
      } else {
        permBits = 0600;
      }
      if ("".equals(fileName)) {
        if (isDir && indentStack.size() == 1) {
          if (perms != null) {
            fs.getPath(root).getFileAttributeView(PosixFileAttributeView.class)
                .setPermissions(FilePerms.permSet(permBits, true));
          }
          continue;
        } else {
          throw new IllegalArgumentException("blank path");
        }
      }
      final int stackEnd = indentStack.size();
      int stackPos = stackEnd;
      while (stackPos > 0 && indent <= indentStack.get(stackPos - 1)) {
        --stackPos;
      }
      Path p;
      if (stackPos == stackEnd) {
        p = paths.get(stackPos - 1).resolve(fileName);
        indentStack.add(indent);
        paths.add(p);
      } else {  // dedent
        if (indent != indentStack.get(stackPos)) {
          throw new IllegalArgumentException("Bad indent:" + line);
        }
        paths.subList(stackPos + 1, stackEnd).clear();
        indentStack.subList(stackPos + 1, stackEnd).clear();
        p = paths.get(stackPos - 1).resolve(fileName);
        paths.set(stackPos, p);
      }
      if (isDir) {
        p.createDirectory(FilePerms.perms(permBits, true));
      } else {
        p.createFile(FilePerms.perms(permBits, true));
        if (contentEncoded != null) {
          JsonSource source = new JsonSource(new StringReader(contentEncoded));
          String content;
          try {
            content = source.expectString();
          } finally {
            source.close();
          }
          OutputStream out = p.newOutputStream();
          try {
            out.write(content.getBytes(Charsets.UTF_8));
          } finally {
            out.close();
          }
        }
      }
    }
    return fs;
  }


  protected static ImmutableGlobSet globs(List<String> globs) {
    ImmutableList.Builder<Glob> b = ImmutableList.builder();
    for (String glob : globs) { b.add(Glob.fromString(glob)); }
    return ImmutableGlobSet.of(b.build());
  }

  protected static ImmutableGlobSet globs(String... globs) {
    return globs(Arrays.asList(globs));
  }
}
