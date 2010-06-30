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

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Produces HTML links for entities that appear in log output.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface EntityLinker {
  /**
   * Optionally writes a link for the given entity to out.
   * @param entityType a type descriptor like {@code "tool"}.
   * @param entityName an entity identifier such as a tool name, or null if
   *    it is the entityType being linked.
   * @param out the channel to which the link HTML should be written.
   * @return true iff {@link #endLink} should be called to close the link
   *    after the entity.
   * @throws IOException if out raises an IOException.
   */
  boolean linkEntity(
      String entityType, @Nullable String entityName, Appendable out)
      throws IOException;
  /**
   * Called if {@link #linkEntity} returned true to close the link after the
   * link body was written by the caller.
   * @param entityType a type descriptor like {@code "tool"}.
   * @param entityName an entity identifier such as a tool name.
   * @param out the channel to which the link HTML should be written.
   * @throws IOException if out raises an IOException.
   */
  void endLink(String entityType, @Nullable String entityName, Appendable out)
      throws IOException;
}
