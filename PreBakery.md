The `prebakery` is a persistent service that services [commands](PreBakeCommand.md) from the [bake](Bake.md) client.

It can be started by following the instructions under [usage](Usage.md).


# Requirements #
Ir requires JDK1.7 because the PreBakery makes extensive use of `java.nio.file` to satisfy its efficiency [goals](Goals.md).
The `prebakery` is an ExtTool outside the ClientRoot so the caveats there apply to upgrading the PreBakery.

As of this writing (March 2010), JDK1.7 is available for most platforms, but for Mac you have to build it yourself.  These [instructions](http://confluence.concord.org/display/CCTR/Build+OpenJDK+Java+1.7.0+on+Mac+OS+X+10.5) can help.

# Responsibilities #
The PreBakery watches the ClientRoot and sees when files change so it can determine which files to rebuild without `stat`ing every file.

It maintains [hashes](http://en.wikipedia.org/wiki/Cryptographic_hash_function) of files under the client root so it can combine file hashes with rule hashes and other hashes to reliably determine which and only which commands need to be run to rebuild files that have changed based on dependencies.

# Client Watcher #
The PreBakery uses the java [watch service](http://java.sun.com/developer/technicalArticles/javase/nio/#6) to determine which files have changed in a reasonably cross-platform way that is independent of the number of files in the ClientRoot on most systems.

The PreBakery will not be efficient on systems for which the `WatchService` is not able to hook into the filesystem.


# File System State #
The PreBakery maintains a hidden [directory](PrebakeDirectory.md) in the client root that it uses to cache state.


# [Hashes](http://en.wikipedia.org/wiki/Cryptographic_hash_function) and Caches #
| **Key** | **Value** |
|:--------|:----------|
| Path under ClientRoot | hash(file content) |
| Path | Non file artifact that depens on it |

These hashes are stored in a [BDB](http://www.oracle.com/database/berkeley-db/je/index.html) instance since some might become large.
The first table disallowes duplicate keys, and the second allows dupes
as more than one non-file artifact can depend on a given file.

# Plans #
A plan is a graph of [Product](Product.md)s, with an edge for each dependency between products.

When in a consistent state, it is acyclic.

Dependencies are inferred based on the intersections of input and output descriptions.

| **Tool** | **Inputs** | **Outputs** | **Notes** |
|:---------|:-----------|:------------|:----------|
| `javac` | `**/*.java` | `lib/**/*.class` | Compiles java source to bytecode |
| `junit` | `lib/tests/**/*.class` | `reports/junit/**/*.html` | Runs unit tests and generates reports |
| `jar` | `lib/src/**/*.class` | `lib/foo.jar` | Packages the non-test outputs |

Given the above rules, we get a simple [dependency graph](PlanGraph.md)
```
        junit
      ⇗
javac
      ⇘
        jar
```

The input and output [globs](Glob.md) of two [Product](Product.md)s are considered to intersect when there exists a string that is in both languages.  It is independent of the actual set of files under the ClientRoot.

This graph is available in DOT format as via the [graph command](PreBakeCommand.md).

# Tool Search Path #
The [tool](ToolFile.md) search path is a path like the PATH or CLASSPATH environment variables -- a list of paths separated by "`:`" on `*`NIX systems and by "`;`" on Windows.

The PreBakery walks this list and looks for JavaScript (`*.js`) files where the basename is a JavaScript identifier.  These are made available via wrapper functions under the `tools` object to code in `PlanFileEnv plan files`.

I.e., if the tools path contains directories like `/foo/` and `/bar/`
```
/foo/
  gcc.js
  javac.js
/bar/
  jar.js
```
then [PlanFile](PlanFile.md)s have available `gcc`, `javac`, and `jar` tools.