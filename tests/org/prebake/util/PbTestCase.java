package org.prebake.util;

import org.prebake.fs.FilePerms;
import org.prebake.js.JsonSource;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.junit.After;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

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
  protected FileSystem fileSystemFromAsciiArt(String cwd, String asciiArt)
      throws IOException {
    FileSystem fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#" + cwd));
    List<Integer> indentStack = Lists.newArrayList(-1);
    List<Path> paths = Lists.newArrayList();
    paths.add(fs.getPath("/"));
    assert asciiArt.indexOf('\t') < 0;
    Pattern lineParts = Pattern.compile(
        ""
        + "( *)"  // indentation
        + "([^/ ]*)"  // file path
        + "(/)?"  // dirs end in /
        + "(?: *\\(([rwx]+)\\))?"  // perms
        + "(?: *(\"(?:[^\"\\\\]|\\\\.)*\"))? *" // content
        );
    for (String line : asciiArt.split("[\r\n]+")) {
      if ("".equals(line.trim())) { continue; }
      Matcher m = lineParts.matcher(line);
      if (!m.matches()) { throw new IllegalArgumentException(line); }
      int indent = m.group(1).length();
      String fileName = m.group(2);
      boolean isDir = m.group(3) != null;
      String perms = m.group(4);
      String contentEncoded = m.group(5);
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
            fs.getPath("/").getFileAttributeView(PosixFileAttributeView.class)
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
}
