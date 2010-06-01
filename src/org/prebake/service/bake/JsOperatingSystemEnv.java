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

import org.prebake.js.MembranableFunction;
import org.prebake.js.SimpleMembranableFunction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Defines a {@link MembranableFunction membranable} interface to the OS
 * for use by JS tool files.  This is exposed as the {@code os} parameter to
 * tools' {@code fire} methods.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class JsOperatingSystemEnv {
  private static ImmutableMap<String, ?> stubProcess(
      final Integer result, final String model) {
    class Holder {
      ImmutableMap<String, ?> jsObj = ImmutableMap.of(
        "help_", (
            "A process that mimics the UNIX " + model + " command by"
            + " yielding " + result + " without consuming any input or"
            + " producing any output."),
        "run", new SimpleMembranableFunction(
            "Returns the process so that calls can be chained",
            "run", "a process object") {
          public Object apply(Object[] args) { return jsObj; }
        },
        "waitFor", new SimpleMembranableFunction(
            "Returns " + result + " without delay",
            "run", "a process object") {
          public Object apply(Object[] args) { return result; }
        });
    }
    return new Holder().jsObj;
  }

  private static final ImmutableMap<String, ?> FAILED_PROCESS_STUB
      = stubProcess(1, "false");
  private static final ImmutableMap<String, ?> PASSED_PROCESS_STUB
      = stubProcess(0, "true");

  public static ImmutableMap<String, ?> makeJsInterface(
      final Path workingDir, MembranableFunction execFn) {
    MembranableFunction mkdirsFn = new SimpleMembranableFunction(
        ""
        + "If any of the given paths do not exist, "
        + "creates a containing directory, creating any "
        + "missing ancestor directories as well.",
        "mkdirs", null, "paths") {
      public Void apply(Object[] args) {
        for (String path : stringsIn(args)) {
          Path p = workingDir.resolve(path);
          try {
            Baker.mkdirs(p, 0700);
          } catch (IOException ex) {
            Throwables.propagate(ex);
          }
        }
        return null;
      }
    };
    MembranableFunction tmpfileFn = new SimpleMembranableFunction(
        ""
        + "Creates and returns a path to a temporary file.",
        "tmpfile", "file", "suffix") {
      public String apply(Object[] args) {
        String suffix = (String) args[0];
        try {
          File f = File.createTempFile(
              "tmp", suffix, new File(workingDir.getParent().toUri()));
          f.deleteOnExit();
          return f.toString();
        } catch (IOException ex) {
          Throwables.propagate(ex);
          return null;
        }
      }
    };
    MembranableFunction dirnameFn = new SimpleMembranableFunction(
        "Returns the parent directory of the given path.",
        "dirname", "path", "path") {
      public String apply(Object[] args) {
        if (args.length == 1 && args[0] instanceof String) {
          Path parent = workingDir.resolve(((String) args[0])).getParent();
          if (parent == null) { return null; }
          return workingDir.relativize(parent).toString();
        } else {
          throw new IllegalArgumentException("Expected 1 string");
        }
      }
    };
    MembranableFunction basenameFn = new SimpleMembranableFunction(
        "Returns the basename of the given path.",
        "basename", "path", "path") {
      public String apply(Object[] args) {
        if (args.length == 1 && args[0] instanceof String) {
          return workingDir.getFileSystem().getPath((String) args[0]).getName()
              .toString();
        } else {
          throw new IllegalArgumentException("Expected 1 string");
        }
      }
    };
    MembranableFunction joinPathsFn = new SimpleMembranableFunction(
        "Returns the normalized path produced by joining the given paths.",
        "joinPaths", "path", "path") {
      public String apply(Object[] args) {
        Iterator<String> it = stringsIn(args).iterator();
        if (!it.hasNext()) { return ""; }
        Path p = workingDir.getFileSystem().getPath(it.next());
        while (it.hasNext()) {
          String pathPart = it.next();
          Path jp = p.resolve(pathPart);
          if (jp == null) { throw new RuntimeException(pathPart); }
          p = jp;
        }
        return p.normalize().toString();
      }
    };
    return ImmutableMap.<String, Object>builder()
        .put("exec", execFn)
        .put("mkdirs", mkdirsFn)
        .put("tmpfile", tmpfileFn)
        .put("dirname", dirnameFn)
        .put("basename", basenameFn)
        .put("joinPaths", joinPathsFn)
        .put("failed", FAILED_PROCESS_STUB)
        .put("passed", PASSED_PROCESS_STUB)
        .build();
  }

  static Iterable<String> stringsIn(Object[] args) {
    ImmutableList.Builder<String> b = ImmutableList.builder();
    for (Object arg : args) {
      if (arg instanceof String) {
        b.add((String) arg);
      } else if (arg instanceof Iterable<?>) {
        for (Object o : (Iterable<?>) arg) {
          if (o instanceof String) {
            b.add((String) o);
          } else {
            throw new ClassCastException(o.getClass().getName());
          }
        }
      } else {
        throw new ClassCastException(arg.getClass().getName());
      }
    }
    return b.build();
  }
}
