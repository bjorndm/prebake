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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A set of {@link Glob}s that can be applied to an input path to
 * figure out the set of globs that match it.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public abstract class GlobSet implements Iterable<Glob> {
  private final PrefixTree prefixTree = new PrefixTree();

  GlobSet() { /* no-op */ }

  /** A copy of the given glob set. */
  GlobSet(GlobSet gset) { copy(gset.prefixTree, prefixTree); }

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

  /**
   * Adds a glob to the set so subsequent calls to {@link #matching} will
   * include it for paths that match glob.
   */
  protected void add(Glob glob) {
    lookup(glob, true).add(glob);
  }

  /**
   * Removes the given glob so that subsequent calls to {@link #matching} will
   * not include it.
   * @return true iff the glob changes.  I.e., true iff glob was added and not
   *     subsequently removed prior to this call.
   */
  protected boolean remove(Glob glob) {
    return lookup(glob, false).remove(glob);
  }

  /**
   * Returns a collection of globs grouped by path prefix.
   * A path prefix is a normalized path to a directory or file that is a
   * (non-strict) ancestor of all paths that match the glob.
   */
  public Multimap<String, Glob> getGlobsGroupedByPrefix() {
    ImmutableMultimap.Builder<String, Glob> b = ImmutableMultimap.builder();
    groupByPrefixInto(prefixTree, b, new StringBuilder());
    return b.build();
  }

  private void groupByPrefixInto(
      PrefixTree t, ImmutableMultimap.Builder<String, Glob> out,
      StringBuilder prefix) {
    Collection<Glob> globs = t.globsByExtension.values();
    if (!globs.isEmpty()) { out.putAll(prefix.toString(), globs); }
    if (!t.children.isEmpty()) {
      int len = prefix.length();
      for (Map.Entry<String, PrefixTree> child : t.children.entrySet()) {
        if (len != 0) { prefix.append('/'); }
        groupByPrefixInto(child.getValue(), out, prefix.append(child.getKey()));
        prefix.setLength(len);
      }
    }
  }

  /**
   * All component globs that match the given path in no-particular order.
   */
  public Iterable<Glob> matching(Path path) {
    PrefixTree t = prefixTree;
    for (Path p : path) {
      PrefixTree child = t.children.get(p.toString());
      if (child == null) { break; }
      t = child;
    }
    String pathStr = normPath(path);
    String extension = extensionFor(pathStr);
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

  /** True iff any glob in the set matches the given normalized path. */
  public boolean matches(Path path) {
    PrefixTree t = prefixTree;
    for (Path p : path) {
      PrefixTree child = t.children.get(p.toString());
      if (child == null) { break; }
      t = child;
    }
    String pathStr = normPath(path);
    String extension = extensionFor(pathStr);
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

  private static void copy(PrefixTree from, PrefixTree to) {
    for (Map.Entry<String, PrefixTree> e : from.children.entrySet()) {
      String s = e.getKey();
      PrefixTree child = to.children.get(s);
      if (child == null) { child = new PrefixTree(s, to); }
      copy(e.getValue(), child);
    }
    to.globsByExtension.putAll(from.globsByExtension);
  }


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
      parent.children.put(prefix, this);
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

  private static String extensionFor(String lastPart) {
    int dot = lastPart.lastIndexOf('.');
    return dot < 0 ? "" : lastPart.substring(dot);
  }

  private static String normPath(Path p) {
    String sep = p.getFileSystem().getSeparator();
    String pathStr = p.toString();
    if (!"/".equals(sep)) {
      pathStr = pathStr.replace(sep, "/");
    }
    return pathStr;
  }

  public Iterator<Glob> iterator() {
    ImmutableList.Builder<Glob> items = ImmutableList.builder();
    iterateOnto(prefixTree, items);
    return items.build().iterator();
  }

  private static void iterateOnto(
      PrefixTree t, ImmutableList.Builder<? super Glob> out) {
    out.addAll(t.globsByExtension.values());
    for (PrefixTree child : t.children.values()) { iterateOnto(child, out); }
  }
}
