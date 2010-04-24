package org.prebake.js;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.MessageQueue;

import java.util.List;
import java.util.Map;

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

  /**
   * @param properties the
   *     {@link java.lang.System#getProperties() system environment}.
   *     At least the "file.separator" must be specified, and it helps
   *     tool files to have access to the {@code os.*} properties.
   */
  public static final ImmutableMap<String, Object> makeEnvironment(
      Map<String, String> properties) {
    final String sep = properties.get("file.separator");
    MembranableFunction globIntersect = new MembranableFunction() {
      public Documentation getHelp() {
        return new Documentation(
            "intersect(globs_a, globs_b)",
            INTERSECT_HELP, null);
      }
      public int getArity() { return 2; }
      public String getName() { return "intersect"; }
      public Object apply(Object[] args) {
        return Glob.overlaps(
            parseGlobs(args[0]), parseGlobs(args[1]));
      }
    };

    MembranableFunction globXformer = new MembranableFunction() {
      public int getArity() { return 2; }
      public Documentation getHelp() {
        return new Documentation(
            "A function that maps inputs to outputs given"
            + " input and output glbos",
            XFORMER_HELP, null);
      }
      public String getName() { return "xformer"; }
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
          b.add(Glob.transform(
              inputGlobs.get(i), outputGlobs.get(i)));
        }
        final List<Function<String, String>> fns = b.build();
        return new MembranableFunction() {
          public int getArity() { return 1; }
          public Documentation getHelp() {
            return new Documentation(
                "glob xfromer",
                "Transforms " + inputGlobs
                + " to " + outputGlobs,
                null);
          }
          public String getName() { return null; }
          public Object apply(Object[] args) {
            String inputPath = (String) args[0];
            if (!"/".equals(sep)) {  // Convert paths from native
              inputPath = inputPath.replace(sep, "/");
            }
            for (Function<String, String> fn : fns) {
              String outputPath = fn.apply(inputPath);
              if (outputPath != null) {
                if (!"/".equals(sep)) {  // Convert DOS paths to native
                  outputPath = outputPath.replace("/", sep);
                }
                return outputPath;
              }
            }
            return null;
          }
        };
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
                .put("xformer", globXformer)
                .build())
        .put(
            "sys",
            ImmutableMap.of(
                "os", ImmutableMap.of(
                    "arch", propget(properties.get("os.arch"), ""),
                    "name", propget(properties.get("os.name"), ""),
                    "version", propget(properties.get("os.version"), ""))))
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
