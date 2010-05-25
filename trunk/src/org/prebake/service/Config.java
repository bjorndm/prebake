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

package org.prebake.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration for the {@link Prebakery} service.
 * <p>
 * The getters below should consistently return the same value.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface Config {
  /**
   * @see <a href="http://code.google.com/p/prebake/wiki/ClientRoot">wiki</a>
   */
  @Nullable Path getClientRoot();
  /**
   * @see
   * <a href="http://code.google.com/p/prebake/wiki/IgnoredFileSet">wiki</a>
   */
  @Nullable Pattern getIgnorePattern();
  /**
   * Permission bit mask for files created by the service.
   * @see <a href="http://code.google.com/p/prebake/wiki/Usage">usage</a>
   */
  int getUmask();
  /**
   * The port on which the service listens for HTTP requests for status info.
   * -1 indicates the service should not listen.
   */
  int getWwwPort();
  /**
   * Search path for
   * <a href="http://code.google.com/p/prebake/wiki/ToolFile">tools</a>.
   */
  @Nonnull List<Path> getToolDirs();
  /** @see <a href="http://code.google.com/p/prebake/wiki/PlanFile">wiki</a> */
  @Nonnull Set<Path> getPlanFiles();
  /**
   * The string used to separate paths, e.g. <tt>:</tt> on *NIX systems or
   * <tt>;</tt> on Windows.
   * @see java.io.File#pathSeparator
   */
  @Nonnull String getPathSeparator();
  /**
   * True iff the localhost should not be required to present credentials before
   * reading information from the source repo from the {@link #getWwwPort HTTP}
   * service.
   */
  boolean getLocalhostTrusted();
}
