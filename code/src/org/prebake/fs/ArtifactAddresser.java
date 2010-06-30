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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A reversible mapping from {@link NonFileArtifact}s to addresses.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface ArtifactAddresser<T extends NonFileArtifact<?>> {
  /** Returns the artifact with the given address. */
  @Nullable T lookup(String address);
  /**
   * The address for the given artifact.
   * This is the dual of {@link #lookup} so
   *     {@code lookup(addressFor(artifact)) == artifact}.
   */
  @Nonnull String addressFor(T artifact);
}
