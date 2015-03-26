# Goals #
  1. Hermetic -- the output of a build should not depend on [source files](SourceFile.md) not specified as inputs
  1. Reliable -- a [generated file](GeneratedFile.md) should be scheduled for rebuilding if any file it depends on has changed or if the rules that specify how it is built have changed
  1. Efficient -- files should be rebuilt unnecessary, and files shouldn't be examined unnecessarily
  1. Complete -- it should be able to build files from any language for which [tools](ExtTool.md) with a command line interface are available
  1. Succinct -- common and easy tasks should be specifiable with little code
  1. Flexible -- it should be possible to specify complex tasks, such as learning optimization flags
  1. Easy to Use -- it should not requiring learning an obscure language or understanding specialized theory
  1. Dynamically Extensible -- user code should be able to integrate home grown tools without breaking out into a different language
  1. Inspectable -- it should expose useful statistics and derivative data to continuous integration tools
  1. Automatable -- it should be drivable by continuous integration tools

# Differences from Existing Systems #
## Build Time is Independent of Source Repository Size ##
When a build system has to `stat` every file in the repo, build is necessarily O(|project|).
By hooking into the filesystem to get updates on files that change, prebake avoids this O(|project|) cost ; if you change one file, your build time depends only on the number of files that depend on that file and the time required to rebuild them.

## Changing a build rule invalidates files it Specifies ##
Prebake uses [hash](http://en.wikipedia.org/wiki/Hash_function)es instead of timestamps, and hashes the definition of the build rule.
So if you change a build rule to pass different arguments to the compiler, prebake will realize that all files that depend on that change need to be rebuilt.

## No build cruft ##
If I have a build rule that says

> compile each `src/*.c` file to a `lib/*.o` file

and I start with a file tree like
```
  src/
    a.c
    b.c
```
I expect after compile to get
```
  src/
    a.c
    b.c
  lib/
    a.o
    b.o
```
but under many build systems, if I rebuild after deleting `b.c` I still get
```
  src/
    a.c
  lib/
    a.o
    b.o
```
There is a lingering generated file: `b.o`.
Prebake does not have this problem.

## Easy Continuous Integration ##
Prebake has two parts.  Unlike ANT or Make, there is a [service](PreBakery.md) always running, and a separate [client](Bake.md).  The persistent service can receive commands from continuous integration tools to kick of builds and test runs on a regular basis, and exports things like the build dependency graph via an HTML interface.

## Dynamic and Analyzable ##
The most flexible way to build is to write a custom shell script.  But build systems based on declarative languages like ANT and Make are much easier to maintain and can be statically analyzed to produce dependency info that helps them scale to large projects.  Prebake gets the advantages of both by using a dynamic language to specify build rules, but sandboxes it and saves artifacts at each stage so that although you can write javaScript code to customize build rules the system can maintain the correctness and analyzability guarantees of a declarative system.