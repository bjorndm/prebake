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

import com.google.common.collect.ImmutableSet;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Utilities for dealing with file permissions.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class FilePerms {
  /**
   * @param permBits POSIX style permission bits where bits 6-8 are RWX
   *     respectively for the owner, bits 3-5 are RWX respectively for all users
   *     in the group, and bits 0-2 are RWX respectively for all other users.
   * @return the file permissions corresponding to permBits.
   */
  public static @Nonnull Set<PosixFilePermission> permSet(
      int permBits, boolean isDirectory) {
    if (isDirectory) {
      // Set the executable bit for any of the UGO blocks that have the read
      // or write bits set.
      int fromRead = (permBits >>> 2) & 0111;
      int fromWrite = (permBits >>> 1) & 0111;
      permBits |= fromRead | fromWrite;
    }
    permBits &= 0777;
    ImmutableSet.Builder<PosixFilePermission> posixs = ImmutableSet.builder();
    for (int k = 0; permBits != 0; ++k, permBits >>>= 1) {
      if ((permBits & 1) != 0) { posixs.add(POSIX_PERMS[k]); }
    }
    return posixs.build();
  }
  /**
   * @param permBits POSIX style permission bits where bits 6-8 are RWX
   *     respectively for the owner, bits 3-5 are RWX respectively for all users
   *     in the group, and bits 0-2 are RWX respectively for all other users.
   * @return the file attributes corresponding to permBits.
   */
  public static @Nonnull FileAttribute<?>[] perms(
      int permBits, boolean isDirectory) {
    return new FileAttribute[] {
        PosixFilePermissions.asFileAttribute(permSet(permBits, isDirectory))
    };
  }

  private static final PosixFilePermission[] POSIX_PERMS = {
    PosixFilePermission.OTHERS_EXECUTE,
    PosixFilePermission.OTHERS_WRITE,
    PosixFilePermission.OTHERS_READ,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_READ,
  };
}
