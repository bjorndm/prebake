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

package org.prebake.service.tools.ext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

/**
 * Finds class names given class files.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
final class ClassNameFinder {
  private final Map<File, String> packages = Maps.newHashMap();

  /** Given a path to a class file, provides a best guess at the class name. */
  String forClassFile(String classFilePath) throws IOException {
    File f = new File(classFilePath);
    String basename = f.getName();
    boolean isClassFile = basename.endsWith(".class");
    if (isClassFile) {
      String packagePrefix = getPackagePrefix(f.getParentFile());
      if (packagePrefix != null) {
        return packagePrefix + basename.substring(0, basename.length() - 6);
      }
    }
    byte[] bytes;
    InputStream in = new FileInputStream(f);
    try {
      bytes = ByteStreams.toByteArray(in);
    } finally {
      in.close();
    }
    String className = new Examiner(f.toString(), bytes).getClassName();

    {
      int dot = className.lastIndexOf('.');
      File parent = f.getParentFile();
      String packagePrefix = className.substring(0, dot + 1);
      while (true) {
        packages.put(parent, packagePrefix);
        if ("".equals(packagePrefix)) { break; }
        parent = parent.getParentFile();
        if (parent == null) { break; }
        dot = packagePrefix.lastIndexOf('.', dot - 1);
        packagePrefix = packagePrefix.substring(0, dot + 1);
      }
    }
    return className;
  }

  private static class Examiner {
    final String filename;
    final byte[] bytes;

    Examiner(String filename, byte[] bytes) {
      this.filename = filename;
      this.bytes = bytes;
    }

    String getClassName() throws IOException {
      int constantPoolCount = shortAt(8);
      int endOfConstantPool = walkConstantPool(constantPoolCount);
      int thisClassName = shortAt(endOfConstantPool + 2);
      int classDefStart = walkConstantPool(thisClassName);
      int classNameIndex = shortAt(classDefStart + 1);
      int classNameStart = walkConstantPool(
          classNameIndex, thisClassName, classDefStart);
      return readUtf8(classNameStart).replace('/', '.');
    }

    private int shortAt(int pos) {
      return ((bytes[pos] & 0xff) << 8) | (bytes[pos + 1] & 0xff);
    }

//  private int intAt(int pos) {
//    return ((bytes[pos] & 0xff) << 24) | ((bytes[pos + 1] & 0xff) << 16)
//        | ((bytes[pos] & 0xff) << 8) | (bytes[pos + 1] & 0xff);
//  }

//  private long longAt(int pos) {
//    return (((long) intAt(pos)) << 32) | intAt(pos + 4);
//  }

    private int walkConstantPool(int limit) throws IOException {
      return walkConstantPool(limit, 1, 10);
    }

    private int walkConstantPool(int limit, int indexHint, int posHint)
        throws IOException {
      int pos = 10;
      int index = 1;
      if (indexHint <= limit) {
        index = indexHint;
        pos = posHint;
      }
      for (; index < limit; ++index) {
        switch (bytes[pos]) {
          case 1:   // CONSTANT_Utf8
            int len = shortAt(pos + 1);
            pos += len + 3;
            break;
          case 5:   // CONSTANT_Long
          case 6:   // CONSTANT_Double
            pos += 9;
            ++index;
            break;
          case 7:   // CONSTANT_Class
          case 8:   // CONSTANT_String
            pos += 3;
            break;
          case 3:   // CONSTANT_Integer
          case 4:   // CONSTANT_Float
          case 9:   // CONSTANT_Fieldref
          case 10:  // CONSTANT_Methodref
          case 11:  // CONSTANT_InterfaceMethodref
          case 12:  // CONSTANT_NameAndType
          case 13:  // CONSTANT_ModuleId
            pos += 5;
            break;
          default: throw new IOException(
              "Bad class file " + filename + " tag=" + bytes[pos] +
              " at " + pos);
        }
      }
      return pos;
    }

    private String readUtf8(int pos) {
      assert (bytes[pos] == 1);
      int len = shortAt(pos + 1);
      return new String(bytes, pos + 3, len, Charsets.UTF_8);
    }
  }

  private String getPackagePrefix(@Nullable File f) {
    if (f == null) { return null; }
    String packagePrefix = packages.get(f);
    if (packagePrefix != null) { return packagePrefix; }
    String parentPackagePrefix = getPackagePrefix(f.getParentFile());
    if (parentPackagePrefix == null) { return null; }
    packagePrefix = parentPackagePrefix + f.getName() + ".";
    packages.put(f, packagePrefix);
    return packagePrefix;
  }

  public static void main(String[] args) throws IOException {
    ClassNameFinder f = new ClassNameFinder();
    for (String arg : args) {
      String className = f.forClassFile(arg);
      System.out.println(arg + " : " + className);
    }
  }
}
