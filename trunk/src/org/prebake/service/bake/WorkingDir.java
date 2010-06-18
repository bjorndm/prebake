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

package org.prebake.service.bake;

import org.prebake.core.Glob;
import org.prebake.core.GlobSet;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.core.MutableGlobSet;
import org.prebake.fs.FsUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Operations for dealing with a temporary working directory.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class WorkingDir {
  /**
   * Compute the list of files under the working directory that match a
   * product's output globs.
   */
  static ImmutableList<Path> matching(
      final Path workingDir, final Set<Path> exclusions,
      ImmutableGlobSet outputMatcher)
      throws IOException {
    // Get the prefix map so we only walk subtrees that are important.
    // E.g. for output globs
    //     [foo/lib/bar/*.lib, foo/lib/**.o, foo/lib/**.so, foo/bin/*.a]
    // this should yield the map
    //     "foo/lib" => [foo/lib/**.o, foo/lib/**.so]
    //     "foo/lib/bar" => [foo/lib/bar/*.lib]
    //     "foo/bin" => [foo/bin/*.a]
    // Note that the keys are sorted so that foo/lib always occurs before
    // foo/lib/bar so that the walker below does not do any unnecessary stating.
    final Map<String, List<Glob>> groupedByDir;
    {
      Multimap<String, Glob> byPrefix = outputMatcher.getGlobsGroupedByPrefix();
      groupedByDir = new TreeMap<String, List<Glob>>(new Comparator<String>() {
        // Sort so that shorter paths occur first.  That way we can start
        // walking the prefixes, and pick up the extra globs just in time when
        // we start walking those paths.
        public int compare(String a, String b) {
          long delta = ((long) a.length()) - b.length();
          return delta < 0 ? -1 : delta != 0 ? 1 : a.compareTo(b);
        }
      });
      Path root = workingDir.getRoot();
      for (String prefix : byPrefix.keySet()) {
        prefix = FsUtil.denormalizePath(root, prefix);
        String pathPrefix = workingDir.resolve(prefix).toString();
        groupedByDir.put(
            pathPrefix, ImmutableList.copyOf(byPrefix.get(prefix)));
      }
    }
    class Walker {
      final ImmutableList.Builder<Path> out = ImmutableList.builder();
      final Set<String> walked = Sets.newHashSet();
      void walk(Path p, GlobSet globs) throws IOException {
        // TODO: handle symbolic links
        String pStr = p.toString();
        List<Glob> extras = groupedByDir.get(pStr);
        if (extras != null) {
          globs = new MutableGlobSet(globs).addAll(extras);
          walked.add(pStr);
        }
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(p);
        if (attrs.isRegularFile()) {
          Path relPath = workingDir.relativize(p);
          if (globs.matches(relPath) && !exclusions.contains(relPath)) {
            out.add(relPath);
          }
        } else {
          for (Path child : p.newDirectoryStream()) { walk(child, globs); }
        }
      }
    }
    Walker w = new Walker();
    for (Map.Entry<String, List<Glob>> e : groupedByDir.entrySet()) {
      String prefix = e.getKey();
      if (w.walked.contains(prefix)) { continue; }  // already walked
      Path p = workingDir.resolve(prefix);
      if (!p.notExists()) { w.walk(p, ImmutableGlobSet.empty()); }
    }
    return w.out.build();
  }
}
