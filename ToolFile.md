# Tool Files #
A tool file is a JavaScript file that appears on the PreBakery's tool search path.  Tool files can kick off an executable, like `gcc` or `javac` to do the work of building a [Product](Product.md)'s outputs.

## Requirements ##
A tool file **must** be a child of a directory listed in the search path that the PreBakery was started with (see [Usage](Usage.md)).  This is not recursive -- if the search path is `foo`, the file `foo/bar.js` is a candidate, but `foo/bar/baz.js` is not.

A tool file name **must** end with `.js` and the rest of its name must be a valid JavaScript identifier.  The name `javac.js` is OK, but `dashed-name.js` is not since dashes are not allowed in JavaScript identifiers.

A tool file **must** parse as valid JavaScript.

The result of evaluating a tool file in the environment described below should be a [ΛSON](YSON.md) value with the signature below.  You can do this by making sure the last statement in your file looks like
```
({  // Parentheses are required.
  help: "Optional Documentation",
  checker: function (action) {  // optional
    ...
    console.warn("Option foo can't have value %s", badValue);
    ...
    return true;  // if the action's options and globs could be OK
  },
  fire: function (inputFiles, product, action, os) {
    // builds the product as described below
  },
})
```
where `inputFiles` is an array of
paths into the TmpWorkspace ; `action` is the frozen [ΛSON](YSON.md) of the
[action](BuildAction.md) running ; and `product` is the frozen ΛSON of
the [product](Product.md) whose action is being evaluated.
`os` is described below.

### `os` <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/bake/JsOperatingSystemEnv.java'>(src)</a></font> ###
The `os` parameter is a bundle of operating system operations.
```
os = {
  exec: function (command, argv...) { ... },  // see below
  mkdirs: function (paths) { ... },     // Like mkdir -p
  tmpfile: function (suffix) { ... },   // a temp filename under the workspace
  dirname: function (path) { ... },     // the parent of the given path
  basename: function (path) { ... },    // the name of the given path
  joinPaths: function (paths) { ... },  // resolves the given paths left-to-right
  passed: { ... },                      // a process object like the UNIX true command
  failed: { ... }                       // a process object like the UNIX false command
};
```

### `os.exec` <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/bake/ExecFn.java'>(src)</a></font> ###
The fire function **may** call `os.exec(path, argv...)` to spawn a
process that runs the executable named by `path` with the given
arguments and returns a ProcessObject.

A call to `os.exec` will not immediately spawn a process.  It
returns a [process builder object](ProcessObject.md) that you can use to configure the process
like
```
({
  pipeTo: function (destProcess) { ... },   // Unix |
  readFrom: function (file) { ... },  // Unix <
  writeTo: function (file) { ... },  // Unix >
  appendTo: function (file) { ... },  // Unix >>
  noInheritEnv: function () { ... },  // Like env -i
  env: function (key, value) { ... },  // Like env $key=$value
  run(),   // Actually starts the process
  kill(),  // Stops the process
  waitFor()  // Waits for the process to exit and gets the result code.
})
```
All methods but the last 3 return the process itself so you can chain
methods: `os.exec('echo', 'foo', 'bar').writeTo('outfile.txt').run().waitFor()`

`os.exec` obeys the usual rules for path resolution ; if the path does not contain a file separator character then it is resolved against the OS search path.

The working directory will be the root of the TmpWorkspace.

The caller **must not** pass any arguments that are paths into the ClientRoot.

The `fire` function **must** return a [ProcessObject](ProcessObject.md) that yields 0 when its `waitFor()` method is called to indicate success or any other value to indicate failure.

The `fire` function **may** log helpful messages to the [JsConsole](JsConsole.md).

## Sanity Checking Actions before build time ##
A tool file **may** define a MobileFunction:
```
  checker: function (action) { ... }
```
that **may** be run to give PlanFile authors quick feedback on what is wrong with [action](BuildAction.md)s that use the tool.

The `check` function **should** return true if the options could be valid, and **may** log helpful messages to the JsConsole for the plan author.

## Environment ##
In addition to the global objects defined by JavaScript, the tool will
have the [common definitions](CommonJsEnv.md).

## Limitations ##
The tool **must not** pass any arguments that are paths into the ClientRoot to `os.exec`.

If any arguments look like they might be or contain paths into the ClientRoot, `os.exec` may raise an exception.