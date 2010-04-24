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

import org.prebake.channel.FileNames;
import org.prebake.core.Glob;
import org.prebake.core.GlobSet;
import org.prebake.fs.FileVersioner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Once a product's actions have been successfully run, figures out the output
 * files and copies them back to the client.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class Finisher {
  private final FileVersioner files;
  private final int umask;
  private final Logger logger;

  // TODO: take into account the ignore pattern here

  Finisher(FileVersioner files, int umask, Logger logger) {
    this.files = files;
    this.umask = umask;
    this.logger = logger;
  }

  ImmutableList<Path> moveToRepo(
      String productName, Path workingDir, final Set<Path> workingDirInputs,
      ImmutableList<Glob> toCopyBack)
      throws IOException {
    ImmutableList<Path> outPaths = outputs(
        productName, workingDir, workingDirInputs, toCopyBack);
    ImmutableList<Path> existingPaths
        = Baker.sortedFilesMatching(files, toCopyBack);

    Set<Path> newPaths = Sets.newLinkedHashSet(outPaths);
    newPaths.removeAll(existingPaths);

    Set<Path> obsoletedPaths = Sets.newLinkedHashSet(existingPaths);
    obsoletedPaths.removeAll(outPaths);

    logger.log(
        Level.FINE, "{0} produced {1} file(s) : {2} new, {3} obsolete.",
        new Object[] {
          productName, outPaths.size(), newPaths.size(), obsoletedPaths.size()
        });

    final Path clientRoot = files.getVersionRoot();

    // Create directories for the new paths
    for (Path p : newPaths) {
      Baker.mkdirs(clientRoot.resolve(p).getParent(), umask);
    }

    // Move the obsoleted files into the archive.
    if (!obsoletedPaths.isEmpty()) {
      Path archiveDir = clientRoot.resolve(FileNames.DIR)
          .resolve(FileNames.ARCHIVE);
      for (Path p : obsoletedPaths) {
        Path obsoleteDest = archiveDir.resolve(p);
        logger.log(Level.FINE, "Archived {0}", obsoleteDest);
        Baker.mkdirs(obsoleteDest.getParent(), umask);
        try {
          clientRoot.resolve(p).moveTo(obsoleteDest);
        } catch (IOException ex) {
          // Junk can accumulate under the archive dir.
          // Specifically, a directory could be archived, and then all attempts
          // to archive a regular file of the same name would file.
          LogRecord r = new LogRecord(Level.WARNING, "Failed to archive {0}");
          r.setParameters(new Object[] { obsoleteDest });
          r.setThrown(ex);
          logger.log(r);
        }
      }
      logger.log(
          Level.INFO, "{0} obsolete file(s) can be found under {1}",
          new Object[] { obsoletedPaths.size(), archiveDir });
    }

    ImmutableList.Builder<Path> outClientPaths = ImmutableList.builder();
    for (Path p : outPaths) {
      Path working = workingDir.resolve(p);
      Path client = clientRoot.resolve(p);
      working.moveTo(client, StandardCopyOption.REPLACE_EXISTING);
      outClientPaths.add(client);
    }

    return outClientPaths.build();
  }

  /**
   * Compute the list of files under the working directory that match a
   * product's output globs.
   */
  private ImmutableList<Path> outputs(
      String productName, final Path workingDir,
      final Set<Path> workingDirInputs, ImmutableList<Glob> toCopyBack)
      throws IOException {
    final GlobSet outputMatcher = new GlobSet();
    for (Glob outputGlob : toCopyBack) { outputMatcher.add(outputGlob); }
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
      String separator = files.getFileSystem().getSeparator();
      for (String prefix : byPrefix.keySet()) {
        if (!"/".equals(separator)) {  // Normalize / in glob to \ on Windows.
          prefix = prefix.replace("/", separator);
        }
        String pathPrefix = workingDir.resolve(prefix).toString();
        groupedByDir.put(
            pathPrefix, ImmutableList.copyOf(byPrefix.get(prefix)));
      }
    }
    class Walker {
      final ImmutableList.Builder<Path> out = ImmutableList.builder();
      final Set<String> walked = Sets.newHashSet();
      void walk(Path dir, GlobSet globs) throws IOException {
        // TODO: handle symbolic links
        String dirStr = dir.toString();
        List<Glob> extras = groupedByDir.get(dirStr);
        if (extras != null) {
          globs = new GlobSet(globs).addAll(extras);
          walked.add(dirStr);
        }
        for (Path p : dir.newDirectoryStream()) {
          BasicFileAttributes attrs = Attributes.readBasicFileAttributes(p);
          if (attrs.isRegularFile()) {
            Path relPath = workingDir.relativize(p);
            if (globs.matches(relPath)) { out.add(relPath); }
          } else if (attrs.isDirectory()) {
            walk(p, globs);
          }
        }
      }
    }
    Walker w = new Walker();
    for (Map.Entry<String, List<Glob>> e : groupedByDir.entrySet()) {
      String prefix = e.getKey();
      if (w.walked.contains(prefix)) { continue; }  // already walked
      Path p = workingDir.resolve(prefix);
      if (p.notExists()) {
        logger.log(
            Level.WARNING,
            "No dir {0} in output for product {1} with outputs {2}",
            new Object[] { p, productName, toCopyBack });
      } else {
        BasicFileAttributes atts = Attributes.readBasicFileAttributes(p);
        if (atts.isDirectory()) {
          w.walk(p, new GlobSet());
        }
      }
    }
    return w.out.build();
  }
}
