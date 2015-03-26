# Process Objects #

A process object is an interface to an external process.

Process objects **must** have `waitFor` and `run` methods with the same semantics as those returned by `os.exec` defined in [ToolFile](ToolFile.md)s.

A process object **may** additionally have the other methods on `os.exec`'s return value like `pipeTo`, `writeTo`, `readFrom`, `appendTo`, `env`, and `kill`.

A trivial NOP process object that behaves like the UNIX `true` command is
```
({
  waitFor: function () { return 0; },
  run: function () { return this; }
})
```
and `os.passed` is just such a process object available in [ToolFile](ToolFile.md)s and  `os.failed` is a similar process object that returns 1 instead like the UNIX `false` command.

## `waitFor()` ##
Blocks and returns 0 to indicate success, and any nonzero integral value to indicate failure.

## `run()` ##
Starts the process running.  It **must** be called before `waitFor()`.
If `run` is called multiple times, the second and subsequent calls have no side-effects.
The `run` method **must** return `this` process object.