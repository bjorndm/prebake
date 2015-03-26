[Plan files](PlanFile.md) execute in an environment where [tools](ToolFile.md)
are exposed as methods of the `tools` object.

They run as if the following code ran before them:
```
var tools = {
  gcc: function (inputGlobs, outputGlobs, options) {

  },
  javac: function (inputGlobs, outputGlobs, options) {

  },
  ...
};
```

If a plan file wishes to `[CommonJsEnv load]` a JavaScript module and
wishes to provide the `tools` object to that module it must do so
explicitly.