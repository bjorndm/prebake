package org.prebake.fs;

import java.nio.file.Path;

/**
 * Utilities for converting from normalized paths to real paths and back.
 *
 * <p>
 * A normalized path is one where <tt>/</tt> is the separator, and the file root
 * is <tt>/</tt>.
 * On UNIX systems, a normalized path is the same as the real path, but on DOS
 * based file-systems, the separator is <tt>\</tt> and the file root is a
 * partition like <tt>C:</tt>.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class FsUtil {
  /** Converts from a real path to a normalized path. */
  public static String normalizePath(Path root, String path) {
    String sep = root.getFileSystem().getSeparator();
    if ("/".equals(sep)) { return path; }
    path = path.replace(sep, "/");
    String rootName = root.toString();
    if (path.startsWith(rootName)) {
      path = "/" + path.substring(rootName.length());
    }
    return path;
  }

  /** Converts from a normalized path to a real path. */
  public static String denormalizePath(Path root, String path) {
    String sep = root.getFileSystem().getSeparator();
    if ("/".equals(sep)) { return path; }
    // Convert DOS paths to native
    boolean isAbs = path.startsWith("/");
    path = path.replace("/", sep);
    if (isAbs) { path = root + path; }
    return path;
  }
}
