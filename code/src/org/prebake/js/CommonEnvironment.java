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

package org.prebake.js;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.MessageQueue;
import org.prebake.fs.FsUtil;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Global definitions available to all JavaScript whether it is in a tool file,
 * or a plan file.
 * Encapsulates a set of symbols that can be provided as part of a JavaScript
 * {@link Executor.Input.Builder#withActuals execution environment}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class CommonEnvironment {
  private CommonEnvironment() { /* not instantiable */ }

  // TODO: maybe grab the docs from the wiki pages.
  private static final String GLOB_HELP = Joiner.on('\n').join(
      "Utility methods for matching file paths.",
      "",
      "  Glob          Matches",
      "  *.foo         Files ending in .foo in this dir",
      "  **/foo.bar    Files named foo.bar under this dir",
      "  foo/*.txt     Filed unding in .txt in directory foo",
      "  **.{c,h}      .c and .h files under this dir",
      "",
      "Expansion",
      "Each occurrence of {...} is expanded before matching.",
      "So {a,b}{1,2} expands to four globs: a1, a2, b1, b2.",
      "",
      "Matching",
      "'*' matches zero or more characters other than a file separator while",
      "'**' zero or more including file separators.",
      "'/' matches a single file separators.  Do not use '\\' on Windows.",
      "Always use '/'.",
      "",
      "Diferrences from other glob systems",
      "There is no '?' operator to match a single character.",
      "To make it possible to efficiently compute the product dependency tree,",
      "globs cannot contain '.' or '..' as path parts, and the '*' and '**'",
      "operators can only appear at the beginning of a glob or after a file",
      "separator."
      );

  private static final String INTERSECT_HELP = Joiner.on('\n').join(
      "True iff there is a path matched by both input glob sets.",
      "Either input may be a string or an Array of strings, but both must be",
      "syntactically valid globs.");

  private static final String XFORMER_HELP = Joiner.on('\n').join(
      "Returns a function (inputPath) that takes an input path and returns",
      "an output path.",
      "",
      "<code>",
      "var xform = glob.xformer('src/**/*.c', 'lib/**/*.o');",
      "xform('src/foo/bar.c');  // => 'lib/foo/bar.o'",
      "xform('src/foo/baz/boo.c');  // => 'lib/foo/baz/boo.o'",
      "xform('src/far.h');  // => null.  Input glob does not match .h files",
      "</code>");

  private static final String MATCHER_HELP = Joiner.on('\n').join(
      "Returns a function (path) that returns true iff the given path",
      "matches glob.");

  private static final String PREFIX_HELP = Joiner.on('\n').join(
      "Given globs, finds the common prefix, the maximal path which",
      "all paths matching any of the globs must be descendants of.");

  private static final String ROOT_OF_HELP = Joiner.on('\n').join(
      "Given globs, finds the common tree root, the portion of the",
      "globs before ///.  The tree root is commonly used to mark",
      "the java package root, root of files in a JAR or ZIP archive",
      "root of an include or lib directory tree, etc.");

  /**
   * @param properties the
   *     {@link java.lang.System#getProperties() system environment}.
   *     At least the "file.separator" must be specified, and it helps
   *     tool files to have access to the {@code os.*} properties.
   */
  public static final ImmutableMap<String, Object> makeEnvironment(
      final Path root, Map<String, String> properties) {
    MembranableFunction globIntersect = new SimpleMembranableFunction(
        INTERSECT_HELP, "intersect", "glob", "globs_a", "globs_b") {
      public Object apply(Object[] args) {
        return Glob.overlaps(
            parseGlobs(args[0]), parseGlobs(args[1]));
      }
    };

    MembranableFunction globXformer = new SimpleMembranableFunction(
        XFORMER_HELP, "xformer", "function (input_path) -> output_path",
        "input_globs", "output_globs") {
      public Object apply(Object[] args) {
        final List<Glob> inputGlobs = parseGlobs(args[0]);
        final List<Glob> outputGlobs = parseGlobs(args[1]);
        if (inputGlobs.size() != outputGlobs.size()) {
          throw new IllegalArgumentException(
              "Inputs do not correspond to outputs:"
              + inputGlobs + " </> " + outputGlobs);
        }
        ImmutableList.Builder<Function<String, String>> b
            = ImmutableList.builder();
        for (int i = 0, n = inputGlobs.size(); i < n; ++i) {
          b.add(Glob.transform(inputGlobs.get(i), outputGlobs.get(i)));
        }
        final List<Function<String, String>> fns = b.build();
        return new SimpleMembranableFunction(
              "Transforms " + inputGlobs + " to " + outputGlobs,
              "xform", "output_path", "input_path") {
          public Object apply(Object[] args) {
            String inputPath = FsUtil.normalizePath(root, (String) args[0]);
            for (Function<String, String> fn : fns) {
              String outputPath = fn.apply(inputPath);
              if (outputPath != null) {
                return FsUtil.denormalizePath(root, outputPath);
              }
            }
            return null;
          }
        };
      }
    };

    MembranableFunction globMatcher = new SimpleMembranableFunction(
        MATCHER_HELP, "matcher", "function (path) -> boolean", "globs") {
      public MembranableFunction apply(Object[] args) {
        MessageQueue mq = new MessageQueue();
        final List<Glob> globs = Glob.CONV.convert(
            args.length == 1 ? args[0] : Arrays.asList(args), mq);
        if (mq.hasErrors()) {
          throw new IllegalArgumentException(
              Joiner.on('\n').join(mq.getMessages()));
        }
        final Pattern p = Glob.toRegex(globs);
        return new SimpleMembranableFunction(
            "True iff the given path matches at least one of " + globs,
            "match", "boolean", "path") {
          public Object apply(Object[] args) {
            return args.length >= 1 && args[0] instanceof String
                && p.matcher((String) args[0]).matches();
          }
        };
      }
    };

    MembranableFunction globPrefix = new SimpleMembranableFunction(
        PREFIX_HELP, "prefix", "path", "globs") {
      public String apply(Object[] args) {
        MessageQueue mq = new MessageQueue();
        List<Glob> globs = Glob.CONV.convert(
            args.length == 1 ? args[0] : Arrays.asList(args), mq);
        if (mq.hasErrors()) {
          throw new IllegalArgumentException(
              Joiner.on('\n').join(mq.getMessages()));
        }
        return FsUtil.denormalizePath(root, Glob.commonPrefix(globs));
      }
    };

    MembranableFunction globRootOf = new SimpleMembranableFunction(
        ROOT_OF_HELP, "rootOf", "path", "globs") {
      public String apply(Object[] args) {
        MessageQueue mq = new MessageQueue();
        List<Glob> globs = Glob.CONV.convert(
            args.length == 1 ? args[0] : Arrays.asList(args), mq);
        if (mq.hasErrors()) {
          throw new IllegalArgumentException(
              Joiner.on('\n').join(mq.getMessages()));
        }
        Iterator<Glob> it = globs.iterator();
        if (!it.hasNext()) { return null; }
        String treeRoot = it.next().getTreeRoot();
        while (it.hasNext()) {
          if (!it.next().getTreeRoot().equals(treeRoot)) { return null; }
        }
        return FsUtil.denormalizePath(root, treeRoot);
      }
    };

    return ImmutableMap.<String, Object>builder()
        .put(
            "glob",
            ImmutableMap.<String, Object>builder()
                .put("help_", doc(
                    "Path matching tools",
                    GLOB_HELP))
                .put("intersect", globIntersect)
                .put("matcher", globMatcher)
                .put("prefix", globPrefix)
                .put("rootOf", globRootOf)
                .put("xformer", globXformer)
                .build())
        .put(
            "sys",
            ImmutableMap.of(
                "os", ImmutableMap.of(
                    "arch", propget(properties.get("os.arch"), ""),
                    "name", propget(properties.get("os.name"), ""),
                    "version", propget(properties.get("os.version"), "")),
                "io", ImmutableMap.of(
                    "path", ImmutableMap.of(
                        "separator",
                        propget(properties.get("path.separator"), ":")),
                    "file", ImmutableMap.of(
                        "separator",
                        propget(properties.get("file.separator"), "/")))))
        .build();
  }

  private static String propget(@Nullable String option, String alternate) {
    return option != null ? option : alternate;
  }

  private static ImmutableList<Glob> parseGlobs(Object jsObj) {
    MessageQueue mq = new MessageQueue();
    ImmutableList.Builder<Glob> out = ImmutableList.builder();
    if (jsObj instanceof Iterable<?>) {
      for (Object item : ((Iterable<?>) jsObj)) {
        out.addAll(Glob.CONV.convert(item, mq));
      }
    } else {
      out.addAll(Glob.CONV.convert(jsObj, mq));
    }
    if (mq.hasErrors()) {
      throw new IllegalArgumentException(
          Joiner.on('\n').join(mq.getMessages()));
    }
    return out.build();
  }

  private static ImmutableMap<String, ?> doc(String summary, String detail) {
    return ImmutableMap.of(
        Documentation.Field.summary.name(), summary,
        Documentation.Field.detail.name(), detail);
  }
}
