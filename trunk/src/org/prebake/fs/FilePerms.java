package org.prebake.fs;

import com.google.common.collect.ImmutableSet;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Utilities for dealing with file permissions.
 *
 * @author mikesamuel@gmail.com
 */
public final class FilePerms {
  /**
   * @param umask a UNIX style umask where bits 6-8 are RWX respectively for
   *     the owner, bits 3-5 are RWX respectively for all users in the group,
   *     and bits 0-2 are RWX respectively for all other users.
   * @return the file attributes corresponding to umask.
   */
  public static FileAttribute<?>[] perms(int umask, boolean isDirectory) {
    if (isDirectory) {
      // Set the executable bit for any of the UGO blocks that have the read
      // or write bits set.
      int fromRead = (umask >>> 2) & 0111;
      int fromWrite = (umask >>> 1) & 0111;
      umask |= fromRead | fromWrite;
    }
    umask &= 0777;
    /*
    if (p != null && "\\".equals(p.getFileSystem().getSeparator())) {
      String name = p.normalize().getName().toString();
      if (name.startsWith(".") && !(".".equals(name) || "..".equals(name))) {
        // TODO: how do we propagate the hidden bit to DOS?
      }
    }
    */
    ImmutableSet.Builder<PosixFilePermission> posixs = ImmutableSet.builder();
    for (int k = 0; umask != 0; ++k, umask >>>= 1) {
      if ((umask & 1) != 0) { posixs.add(POSIX_PERMS[k]); }
    }
    return new FileAttribute[] {
        PosixFilePermissions.asFileAttribute(posixs.build())
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
