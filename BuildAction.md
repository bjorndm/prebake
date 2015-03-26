# Build Action <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/plans/Action.java'>(src)</a></font> #

An action is a step in a recipe that involves running a [tool](ToolFile.md).  Actions are created in plan files by invoking a tool file wrapper, e.g.
```
tools.gcc(
    /* inputs */  [ 'src/foo/*.cc', 'includes///**.h', ... ],
    /* outputs */ [ 'lib/foo/*.o', ... ],
    /* options */ { "-O": 2 });
```

Each [tool file](ToolFile.md) appears as a method of the `tools` object with the signature `function myTool(inputGlobs, outputGlobs, options)`.  Calling a tool method creates an action.

[Plan files](PlanFile.md) bundle actions into [products](Product.md) by defining a [ΛSON](YSON.md) object like
```
({
  myProduct1: { actions: [ tools.myTool('inputs/*', 'outputs/*', {option: value}),
  myProduct2: ...
})
```