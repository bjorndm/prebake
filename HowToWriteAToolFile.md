# How To Write a [Tool File](ToolFile.md) #

## Source ##
Builtin tools go in the [tools](http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/tools) directory.  They have to have a name that is a java identifier with `.js` added to the end.

See the [tool file wiki](ToolFile.md) for information about the JavaScript APIs available to tool files.

## Choose a short tool name reminiscent of the command line ##
It's easier to read/write `tools.gcc(...)` than `tools.gnu_c_compiler(...)`.  Be nice.

There might be tool name conflicts, but that's why we've got a tool search path.  So that tool naming conflicts are no worse than the command line executables they abstract.

## Use [Glob](Glob.md)s and tree roots ##
Prefer globs to options.  `tools.myCppCompiler(['src/**.cc', 'include///**.h'], ...)` is better than `tools.myCppCompiler(['src/**.cc', 'include/**.h'], ..., { 'I': [ 'include' ] })`

Remember, the only files available under the TmpWorkspace are going to be those specified as inputs or built by previous [action](BuildAction.md)s.

You can use `glob.rootOf` to get the portion of a glob before the `///` that starts a directory tree.  See [Glob](Glob.md) for the ways tree roots can come in handy.

## Options should be the same as command line flags if possible ##
But they should be JS identifiers, so they can be put in an object constructor without quotes, e.g. `{ option_name: ... }` instead of `{ "option-name": ... }`

## Make output directories for your users ##
Don't require your users to do `mkdir` tasks themselves.  If you see that a directory is required by the tool you're running, make it.

## Write a `check` method ##
Checkers are a good way of catching problems earlier and educating your tool's users about features they might not know about.  Use the [console](JsConsole.md) to give them helpful info.

The `check` method is a [mobile function](MobileFunction.md) like `function (action) { ... return <boolean>; }` that returns `true` if the action's options and input and output globs might be correct.

## Return a ProcessObject from `fire` ##
If you don't and it doesn't return 0 as the exit code, then your tool will be considered to have failed which means the [product](Product.md)'s outputs won't be copied back and it will stay unbuilt.

When checking for errors and edge cases, you can use `os.failed` and `os.passed` as process objects.  E.g.,
```
  if (hasError) { return os.failed; }
  if (inputs.length === 0) {
    // Nothing to do.
    return os.passed;
  }
  return os.exec(...);
```

## Beware of passing zero inputs ##
Many command line tools behave very differently when passed zero arguments than when passed one.

For example, `ls` when given one or more files as arguments will print the paths to those files, but when given none will print the contents of the current working directory.

See the code snippet above for a simple way to use `os.passed` to deal with this.


## Wait for your processes ##
If you spawn multiple processes using `os.exec`, you need to `.waitFor` the result.
And check the exit code.  0 normally indicates success, so one idiom is to do the following at the end of your `fire` method:
```
  if (os.exec(...).run().waitFor() !== 0) { throw new Error(...); }  // Make sure all ancillary processes complete. 
  return os.exec(...);  // Return the main process so the product's bake method can redirect it.
```

Or alternately, you can spawn a bunch of processes and return a single wrapper ProcessObject that waits for them:
```
    var processes = [];
    for (...) {
      processes.push(os.exec(...).run());
    }
    return {
      run: function () {
        for (var i = 0, n = processes.length; i < n; ++i) { process[i].run(); }
        return this;
      },
      waitFor: function () {
        for (var i = 0, n = processes.length; i < n; ++i) {
          if (processes[i].waitFor() !== 0) {
            throw new Error(...);
          }
        }
        return 0;  // All happy
      }
    };
```

## Do **not** hardcode executable paths ##
The PreBakery is a persistent process.  Users can point it at the right version of executables by passing in an appropriate PATH once.  If your tool doesn't exist, the system will log that fact and suggest changing the PATH.

If you really have to, you can use the `.env("PATH", value)` method of process to add your own path to the end to provide a useful default.

## Thou shalt **Not** covet thy [client's files](ClientRoot.md) ##
Stick to the TmpWorkspace.  Files under the client root are off limits to tools.