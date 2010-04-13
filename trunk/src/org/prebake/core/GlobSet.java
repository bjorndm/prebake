package org.prebake.core;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A set of {@link Glob}s that can be applied to an input path to
 * figure out the set of globs that match it.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public class GlobSet {
  final PrefixTree prefixTree = new PrefixTree();

  private PrefixTree lookup(Glob glob, boolean create) {
    List<String> parts = glob.parts();
    PrefixTree t = prefixTree;
    for (int i = 0, n = parts.size(); i < n; ++i) {
      String s = parts.get(i);
      if ("/".equals(s)) { continue; }
      if (s.charAt(0) == '*') { break; }
      if (i + 1 < n && parts.get(i).charAt(0) == '*') { break; }
      PrefixTree child = t.children.get(s);
      if (child == null) {
        if (create) {
          child = new PrefixTree(s, t);
        } else {
          break;
        }
      }
      t = child;
    }
    return t;
  }

  public void add(Glob glob) {
    lookup(glob, true).add(glob);
  }

  public boolean remove(Glob glob) {
    return lookup(glob, false).remove(glob);
  }

  /** All globs that match the given path in no-particular order. */
  public Iterable<Glob> matching(Path normPath) {
    PrefixTree t = prefixTree;
    for (Path p : normPath) {
      PrefixTree child = t.children.get(p.toString());
      if (child == null) { break; }
      t = child;
    }
    String pathStr = normPath.toString();
    String extension = extensionFor(normPath);
    if ("".equals(extension)) { extension = null; }
    ImmutableList.Builder<Glob> b = ImmutableList.builder();
    for (; t != null; t = t.parent) {
      if (extension != null) {
        for (Glob g : t.globsByExtension.get(extension)) {
          if (g.match(pathStr)) { b.add(g); }
        }
      }
      for (Glob g : t.globsByExtension.get("")) {
        if (g.match(pathStr)) { b.add(g); }
      }
    }
    return b.build();
  }

  public boolean matches(Path normPath) {
    PrefixTree t = prefixTree;
    for (Path p : normPath) {
      PrefixTree child = t.children.get(p.toString());
      if (child == null) { break; }
      t = child;
    }
    String pathStr = normPath.toString();
    String extension = extensionFor(normPath);
    if ("".equals(extension)) { extension = null; }
    for (; t != null; t = t.parent) {
      if (extension != null) {
        for (Glob g : t.globsByExtension.get(extension)) {
          if (g.match(pathStr)) { return true; }
        }
      }
      for (Glob g : t.globsByExtension.get("")) {
        if (g.match(pathStr)) { return true; }
      }
    }
    return false;
  }

  private static final Supplier<List<Glob>> GLOB_LIST_SUPPLIER
      = new Supplier<List<Glob>>() {
        public List<Glob> get() { return Lists.newArrayList(); }
      };

  /** A node in a prefix or suffix tree. */
  @ParametersAreNonnullByDefault
  private static final class PrefixTree {
    final Map<String, PrefixTree> children = Maps.newHashMap();
    final Multimap<String, Glob> globsByExtension = Multimaps.newListMultimap(
        Maps.<String, Collection<Glob>>newHashMap(), GLOB_LIST_SUPPLIER);
    final String prefix;
    final @Nullable PrefixTree parent;

    PrefixTree() {
      this.prefix = "";
      this.parent = null;
    }

    PrefixTree(String prefix, PrefixTree parent) {
      this.prefix = prefix;
      this.parent = parent;
      if (parent != null) {
        parent.children.put(prefix, this);
      }
    }

    void add(Glob glob) {
      globsByExtension.put(extensionFor(glob), glob);
    }

    boolean remove(Glob glob) {
      boolean changed = globsByExtension.remove(extensionFor(glob), glob);
      for (PrefixTree t = this; t.parent != null; t = t.parent) {
        if (t.globsByExtension.isEmpty() && t.children.isEmpty()) {
          t.parent.children.remove(t.prefix);
        } else {
          break;
        }
      }
      return changed;
    }
  }

  private static String extensionFor(Glob glob) {
    List<String> parts = glob.parts();
    String lastPart = parts.get(parts.size() - 1);
    if (lastPart.charAt(0) == '*') { return ""; }
    return extensionFor(lastPart);
  }

  private static String extensionFor(Path p) {
    return extensionFor(p.getName().toString());
  }

  private static String extensionFor(String lastPart) {
    int dot = lastPart.lastIndexOf('.');
    return dot < 0 ? "" : lastPart.substring(dot);
  }
}
