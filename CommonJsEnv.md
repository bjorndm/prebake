# Common JavaScript Environment <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/js/CommonEnvironment.java'>(src)</a></font> #

All JavaScript files run by prebake can use global definitions to make it easy to [debug](JsConsole.md), deal with file paths and [globs](Glob.md).

The API available has the same structure as produced by the below.
```
// Debugging tools
var console = {
  debug: function (message, ...) { ... },
  error: function (message, ...) { ... },
  info: function (message, ...) { ... },
  log: function (message, ...) { ... },
  warn: function (message, ...) { ... },
  dir: function (obk) { ... },
  assert: function (bool, opt_message) { ... },
  group: function (label) { ... },
  groupEnd: function (label) { ... },
  time: function (label) { ... },
  timeEnd: function (label) { ... },
  trace: function () { ... }
};

// Logs help info about the given object.
function help(obj) { ... }

var glob = {
  // True if there is a path matched by any glob in a_globs
  // also matched by a glob in b_globs.
  intersect: function (a_globs, b_globs) { ... };
  // returns a function that returns true if a path passed in is matched by
  // any of the given glob
  matcher: function (globs { return function (path) { ... }; }
  // returns a path which all paths that match any of the globs must be under.
  prefix: function (globs) { ... }
  // returns the tree root of the given globs, or null if there is no common tree root.
  // See the Glob wiki for the definition of tree root.
  rootOf: function (globs) { ... }
  // returns a function that maps input paths that match the given globs to
  // output paths.
  // E.g.,
  // var xform = glob.xformer('src/**/*.c', 'lib/**/*.o');
  // xform('src/foo/bar.c');  // => 'lib/foo/bar.o'
  // xform('src/foo/baz/boo.c');  // => 'lib/foo/baz/boo.o'
  // xform('src/far.h');  // => null.  Input glob does not match .h files
  xformer: function (inGlobs, outGlobs) { return function (inPath) { ... } }
};

var sys = {
  os: {
    arch: 'i386',
    name: '...ix',
    version: '1.0.0'
  },
  io: {
    file: { separator: '/' }.
    path: { separator: ':' }
  }
};
```

## `console` ##
The [console](JsConsole.md) object can be used for debugging.

## <a><code>help</code></a> ##
The `help` function logs information about the object passed in.

## <a><code>load</code></a> ##
`load(relativePath)`
The `load` function loads a JavaScript file.
If a plan file or tool file uses `load`, then files that depend on
that plan file or tool file will also depend on the loaded JavaScript
file.

It takes a path that is resolved relative to the current script.
So calling `load("bar.js")` from `foo.js' will find `bar.js` in the
same directory as `bar.js`.

The `load` function does not execute the loaded module immediately,
but instead returns a function that when called executes the module
using the input as the global object.
```
  // Load the module foo and give it a snapshot of this module's global object.
  load('foo.js')(this);

  // Load the module bar but give it fewer or different globals.
  // The loaded module will still get the same intrinsics: Array, Object, ...
  load('bar.js')({ myGlobal: myGlobal, anotherGlobal: differentValue });
```

If the loaded module is not under the [client root](ClientRoot.md) then
all the [ExtTool](ExtTool.md) caveats apply.