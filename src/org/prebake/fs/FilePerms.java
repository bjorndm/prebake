package org.prebake.fs;

import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

public final class FilePerms {
  public static FileAttribute<?>[] perms(
      int umask, boolean isDirectory, Path p) {
    if (isDirectory) {
      // Set the executable bit for any of the UGO blocks that have the read
      // or write bits set.
      // r wxrw xrwx
      // 1 8421 8421
      int fromRead = (umask >>> 2) & 0x49;
      int fromWrite = (umask >>> 1) & 0x49;
      umask |= fromRead | fromWrite;
    }
    umask &= 0x1ff;
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
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.OTHERS_READ,
    PosixFilePermission.OTHERS_WRITE,
    PosixFilePermission.OTHERS_EXECUTE,
  };
}
