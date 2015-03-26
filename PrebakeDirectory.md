# `.prebake` directory structure #

```
<client-root>/
    .prebake/
        port
        cmdline
        token
        archive/
          ...
        logs/
          ...
        00000000.jdb
        je.lck
```

The PreBakery service creates a directory in the ClientRoot on startup and, except for `cmdline`, deletes it on shutdown.

### `<client-root>/.prebake/port` ###

Contains a port number on the local machine.  The [bake](Bake.md) command connects to this port to kick off builds using the PreBakeCommand grammar.

### `<client-root>/.prebake/cmdline` ###

Contains the `argv` passed to the prior PreBakery so the `bake` command can start a PreBakery if none is running.

### `<client-root>/.prebake/token` ###

Contains a random string which the `bake` command sends with PreBakeCommand to demonstrate that it has at least a minimal level of access to the ClientRoot.

### `<client-root>/.prebake/archive` ###

Contains files that were deleted because they matched the outputs glob of a
[product](Product.md) but were not produced by the product.
This happens, for example, when a product builds `*.o` files from
`*.cc` files, and the developer deletes a `*.cc` file causing the next
build to produce one less `*.o` file.

If a developer accidentally switches outputs and inputs in a build
rule in strange ways, they could lose data.  If that happens to you, look
under the `archive` directory.

### `<client-root>/.prebake/logs` ###

Contains a `.log` file for each [tool](ToolFile.md) and [product](Product.md) that has been built containing pertinent log info.

### `<client-root>/.prebake/je.*}} and {{{<client-root>/.prebake/*.jdb` ###

Files for a [Berkeley DB](http://www.oracle.com/database/berkeley-db/index.html) with tables mapping file paths under [&lt;client-root&gt;](ClientRoot.md) to
MD5 hashes of those files, and non file artifacts to the files they
depend on.