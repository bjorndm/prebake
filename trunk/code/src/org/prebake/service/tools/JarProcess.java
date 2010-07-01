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

package org.prebake.service.tools;

import org.prebake.core.Glob;
import org.prebake.core.MutableGlobSet;
import org.prebake.fs.FilePerms;
import org.prebake.fs.FsUtil;
import org.prebake.service.tools.InVmProcess;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * A replacement for the Java JAR tool.
 * <p>
 * The JAR tool sucks because it cannot properly generate jar manifests due to
 * the bizarre incomplete manifest file specification format it uses, and
 * because it cannot unpack different groups of files into different output
 * directories.
 * <p>
 * This class is used by the {@code jar} tool instead of spawning a jar process.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class JarProcess implements InVmProcess {
  public byte run(Path cwd, String... argv) throws IOException {
    String operation = argv[0];
    if ("c".equals(operation)) {
      return createJar(cwd, argv);
    } else if ("x".equals(operation)) {
      return extractJar(cwd, argv);
    } else {
      throw new IllegalArgumentException(operation);
    }
  }

  static byte createJar(Path cwd, String... argv) throws IOException {
    int pos = 0;
    int last = argv.length - 1;
    String outPath = argv[++pos];
    int nManifestEntries = Integer.parseInt(argv[++pos]);
    ZipOutputStream out;
    if (nManifestEntries >= 0) {
      if ((nManifestEntries & 1) == 1) { throw new IllegalArgumentException(); }
      Manifest mf = new Manifest();
      Map<Object, Object> attrs = mf.getMainAttributes();
      attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      for (int end = pos + nManifestEntries; pos < end;) {
        attrs.put(new Attributes.Name(argv[++pos]), argv[++pos]);
      }
      out = new JarOutputStream(
          cwd.resolve(outPath).newOutputStream(
              StandardOpenOption.CREATE_NEW,
              StandardOpenOption.TRUNCATE_EXISTING),
          mf);
    } else {
      out = new ZipOutputStream(
          cwd.resolve(outPath).newOutputStream(
              StandardOpenOption.CREATE_NEW,
              StandardOpenOption.TRUNCATE_EXISTING));
    }
    try {
      Set<String> dirs = Sets.newHashSet();
      Path root = cwd.getRoot();
      while (pos < last) {
        String basePath = argv[++pos];
        Path baseDir = "".equals(basePath) ? cwd : cwd.resolve(basePath);
        int nFiles = Integer.parseInt(argv[++pos]);
        for (int end = pos + nFiles; pos < end;) {
          String file = argv[++pos];
          Path p = baseDir.resolve(file);
          mkdirs(baseDir, p.getParent(), dirs, out);
          long size = (
              (Number) p.getAttribute("size", LinkOption.NOFOLLOW_LINKS))
              .longValue();
          ZipEntry e = new ZipEntry(FsUtil.normalizePath(root, file));
          e.setSize(size);
          out.putNextEntry(e);
          InputStream in = p.newInputStream();
          try {
            if (size != ByteStreams.copy(in, out)) {
              throw new IOException("Version skew jarring file " + p);
            }
          } finally {
            in.close();
          }
          out.closeEntry();
        }
      }
    } finally {
      out.close();
    }
    return 0;
  }

  private static void mkdirs(
      Path baseDir, Path p, Set<String> dirs, ZipOutputStream out)
      throws IOException {
    if (p.equals(baseDir) ) { return; }
    String key = baseDir.relativize(p).toString();
    if (dirs.contains(key)) { return; }
    mkdirs(baseDir, p.getParent(), dirs, out);
    out.putNextEntry(new ZipEntry(
        FsUtil.normalizePath(baseDir.getRoot(), key) + "/"));
    dirs.add(key);
  }

  static byte extractJar(Path cwd, String... argv) throws IOException {
    int pos = 0;
    int last = argv.length - 1;

    Path root = cwd.getRoot();

    String jarFile = argv[++pos];

    MutableGlobSet globs = new MutableGlobSet();
    Multimap<Glob, Path> outRoots = Multimaps.newListMultimap(
        Maps.<Glob, Collection<Path>>newHashMap(), new Supplier<List<Path>>() {
          public List<Path> get() { return Lists.newArrayList(); }
        });
    while (pos < last) {
      String globStr = argv[++pos];
      Glob g = Glob.fromString(globStr);
      String treeRoot = g.getTreeRoot();
      if (!"".equals(treeRoot)) {
        globStr = globStr.substring(treeRoot.length());
        if (globStr.startsWith("///")) { globStr = globStr.substring(3); }
        g = Glob.fromString(globStr);
      }
      if (outRoots.get(g).isEmpty()) { globs.add(g); }
      outRoots.put(g, cwd.resolve(FsUtil.denormalizePath(root, treeRoot)));
    }

    ZipInputStream in = new ZipInputStream(
        cwd.resolve(jarFile).newInputStream());
    try {
      Set<Path> dests = Sets.newLinkedHashSet();
      for (ZipEntry e; (e = in.getNextEntry()) != null;) {
        Path path = root.getFileSystem().getPath(
            FsUtil.denormalizePath(root, e.getName()));
        dests.clear();
        for (Glob g : globs.matching(path)) {
          dests.addAll(outRoots.get(g));
        }
        if (e.isDirectory()) {
          for (Path dest : dests) { mkdirs(dest.resolve(path)); }
        } else {
          if (!dests.isEmpty()) {
            OutputStream[] outs = new OutputStream[dests.size()];
            try {
              Iterator<Path> it = dests.iterator();
              for (int i = 0; i < outs.length; ++i) {
                Path outFile = it.next().resolve(path);
                mkdirs(outFile.getParent());
                outs[i] = outFile.newOutputStream(
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.TRUNCATE_EXISTING);
              }
              byte[] buf = new byte[4096];
              for (int nRead; (nRead = in.read(buf)) > 0;) {
                for (OutputStream os : outs) { os.write(buf, 0, nRead); }
              }
            } finally {
              for (OutputStream os : outs) {
                if (os != null) { Closeables.closeQuietly(os); }
              }
            }
          }
          in.closeEntry();
        }
      }
    } finally {
      in.close();
    }
    return 0;
  }

  private static void mkdirs(Path p) throws IOException {
    if (p.notExists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent); }
      p.createDirectory(FilePerms.perms(0700, true));  // Working dir perms
    }
  }
}
