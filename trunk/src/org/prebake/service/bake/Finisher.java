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
import org.prebake.fs.FileVersioner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
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
    // TODO: respect the ignorable pattern for outPaths.

    // Compute the list of files under the working directory that match a
    // product's output globs.
    ImmutableList<Path> outPaths = WorkingDir.matching(
        workingDir, workingDirInputs, toCopyBack);
    // Compute the set of files that are already in the client directory.
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
}
