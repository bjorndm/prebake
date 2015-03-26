# Temporary Workspace #

When building a [product](Product.md), the [prebakery](PreBakery.md)
  * creates a temporary directory called the temporary workspace
  * copies all files under ClientRoot that match the Product's input globs to the temporary workspace.
  * loads the needed [tools](ToolFile.md) into a JavaScript interpreter with an `exec` function that will run external processes to build the outputs. (Aborts if any tool file returns a value other than `true`.)
  * copies all files under ClientRoot that match the Product's output globs from the temporary workspace back to the ClientRoot.
  * moves any files that matched the Product's output globs that were not in the temporary workspace to the `archive` [directory](PrebakeDirectory.md).
  * if all the input file hashes still match those that were present at the time the build started, mark the product valid.

Since build tools never† touch the ClientRoot directly, we can get
repeatable builds, and build products that have no dependencies in
common in parallel.

(†) - tool files can create paths into the ClientRoot.  It is not
unmounted, sor unreadable.  Don't do that.  There are
[checks](http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/bake/WorkingFileChecker.java)
that will try to prevent this from happening accidentally.