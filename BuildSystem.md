# What is a Build System? #

A build system permutes a file system to build output files from a set of source files.
The build system uses a set of rules to produce the output files.

The set of rules specify a transformation from an input file tree to an output file tree.

> BuildSystem : file-tree → file-tree'

Build systems typically allow a developer to build portions of file-tree' so they can produce an executable for one architecture but not another.  So there exists an ideal file-tree containing all possible outputs : file-tree∗.

This conceptual file-tree l is useful for a number of reasons:
  1. correctness.  To have confidence that their testing will find bugs before production, they need to know that the software they test on their local environment during development is similar to what the master build will produce for production runs, and not change based on the order in which they edit and manipulate source files.  The functional model is independent of the history of the file tree.
  1. efficiency.  To iterate quickly when working in a large codebase, builds need to be fast.  The functional definition of a build gives us a minimal criterion for deciding when to rebuild files.
  1. repeatability.  For developers to reproduce bugs, they need to be able to check out and build the same version of software that has shipped.  The functional model is independent of history.

## Rules and Targets ##
Most build systems have the concept of rules.
A rule specifies how to create output files from input files.
So in `make`, a rule might be
```
  %.o : %.c
          $(CC) $< -o $@
```
which says "invoke the C compiler with a C source file to produce an output object file."

A target is the application of a rule to a set of files.  In Ant, there is no difference between a rule and a target, but with Make there are generic rules like the above that say how to produce one kind of file from another kind but there are also rules that produce specific files.

So a target then is a transformation from a group of input files (possibly the whole file-system as in both Ant and Make) to a group of output files.

> Target : input-files -> output-files

Most build systems produce targets by spawning a process and consider it to have successfully built if its exit status indicates success.

## Consistency ##

A build system is **consistent** when the developer's real file-tree contains all the source files (file-tree) and a subset of the files in the conceptual file-tree∗.

> isConsistent(file-tree') == (file-tree' ⊃ file-tree) && (file-tree' ⊂ file-tree∗)

So the source file-tree is trivially consistent.

Many common build systems have no way to know whether they are consistent ; if you build some output files, change a source file, and rebuild you might get a different result than if you had not done the first build.

If we start from a known consistent state (the source file-tree) and build targets only when all their inputs are available then we can move from one consistent state to another when
  1. no target spawned a process until that process's inputs were available (prereqs hold)
  1. no process modifed a source file or a previously generated output (non-interference)
  1. all processes spawned by a build target have exited (no unflushed content)
  1. all processes successfully produced their output (local consistency)

## Scheduling ##
So a build system can maintain consistency by keeping an eye on the processes it spawns to build files.

But the real test of a build system is in how it moves from an inconsistent state back to a consistent state.

Inconsistency arises in two ways:
  1. the developer changes or deletes a source file.  Now output files that depend on the old version are a source of inconsistency
  1. the developer changes a rule or target.  Any output files the old version specified are a source of inconsistency.

Given the above functional definition of a build system, the second is not an inconsistency in a build system, but the replacing of a consistent build system with an inconsistent one.

To move from an inconsistent build to a consistent build, a build system needs to maintain the following additional invariants.
  1. a target is scheduled to be rebuilt if one or more of its inputs has changed in a material way
  1. a target is scheduled to be rebuilt when a source file is created that should be an input is rebuilt
  1. when a target's inputs change in such a way that that target no longer specifies an output, that output is deleted or the target is rebuilt and building the target deletes obsolete outputs.

As an example, consider a file tree that has a `src` directory with files that contain numbers, and that produces output files that contain corresponding files incremented by 1.
```
  src/
    a : 4
  out/
    a : 5
```

If the developer creates a file `src/b` the system is consistent, but would be more useful if there were a corresponding `out/b` file.
```
  src/
    a : 4
    b : 22
  out/
    a : 5
    # missing file out/b
```

If the developer changes `src/a` the system is inconsistent until `out/a` is deleted or changed.
```
  src/
    a : 7    # changed
    b : 22
  out/
    a : 5    # needs to be 8
    b : 23
```

If the developer deletes `src/b` the system is inconsistent until `out/b` is deleted.
```
  src/
    a : 7
    # deleted
  out/
    a : 8
    b : 23   # obsolete file
```

Most build systems including Make and Ant do a good job on the first two.  They use file modification timestamps to determine which files have changed.  Timestamps are not bullet-proof, but work reasonably well in practice.  Both though do nothing to preserve the third invariant.

## Parameterized Rules ##
### Partial Runs ###
Many build systems like Ant allow parameterized builds.  If you run tests under JUnit you can write a build rule that will filter tests by looking at system properties that change from one invocation of ant to the next.
```
  $ ant -Dtest.filter=FooTest runtests
```
to run only tests in the `FooTest` class and generate an output report with only details of that test.  If run without `-D...` (which sets a Java system property) it would run all tests and the output files, the test report, would contain reports for all tests.

### L10N/I18N ###
Often, the build system is involved in localizing a product into multiple languages.
I might run
```
  $ LANG=de make all
```
to build the German language version of my application.  In this case, an environment variable was used to parameterize the build.

### Cross-Compiles ###
Some projects need to ship multiple versions of the same dynamically libraries for different architectures, e.g. to generate a directory structure like
```
  lib/
    arch/
      win86/
        foo.dll
      linux-64/
        foo.so
      macos-x86/
        foo.dylib
```
but want to share most of the build rules by using a rule where the architecture is an input parameter.

### Problems with Incremental Compilation ###
Parameterized builds can be very useful.  It means that one build rule can serve many purposes.
But it blows incremental consistency out of the water.  The only way for a build system that is parameterized this way to approximate the file tree based on our functional model is to rebuild from clean every time a parameter changes.  Ant does not do this, sacrificing correctness and repeatability, but it is common practice with Make based builds to use a `configure` script to generate a Makefile with parameters baked in.

### Functional parameterized targets ###
We can preserve the functional model of a build system and enable the use cases that inspire parameterized build rules by making sure that parameters are explicit in the file system.

In the case of test filters, the test filter becomes part of the test-report path.
```
  test-reports/
    all/
      report.html
    only-FooTest/
      report.html
```

In the case of the localized version of the applications, we would need to include the locale in the file tree.
```
  docs/
    de/
      index.html
    en/
      index.html
    ...
```
mirroring the way architecture specific files are already bundled in the file tree.

Prebake will expose (TODO: update once done) parameterized build rules by using glob-relations.
A glob relation is a set of input globs, a set of output globs, and parameter meta-data.
```
{
  inputs:  ['src/l10n/${locale}/translation-bundle',
            'src/docs/index.template.html'],
  outputs: 'out/docs/${locale}/index.html',
  parameters: {
    locale: ['de', 'en', ...]  // Acceptable values
  }
}
```

Figuring out which inputs are needed to produce which outputs is a simple matter of substitution.  Some parameters might have an infinite range, which is why we need to accept that for a parameterized build, the ideal file-system really is an idealization and strive for consistency in the set of files needed.  Those parameters with infinite range should have a sensible default.  But for other parameters, the most important ones for release builds, the set of acceptable values is small.

## Where Prebake falls short ##
TODO: assumptions about invariance of installed tools
TODO: granularity of targets