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

package org.prebake.fs;

/**
 * An artifact derived from files that is not represented in the file-system
 * itself, e.g. a tool definition, a product,
 * or an edge in the dependency graph.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface NonFileArtifact {
  /**
   * @param valid false if the artifact is now invalid, and true if it is now
   *    valid.
   * @see FileVersioner#update(
   *     ArtifactAddresser, NonFileArtifact, java.util.Collection,
   *     org.prebake.core.Hash)
   */
  void markValid(boolean valid);
}
