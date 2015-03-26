# Plan Files #

A plan file specifies how to build outputs.  See one of <a href='http://code.google.com/p/prebake/source/browse/trunk/code/Bakefile.js'>prebake's plan file</a> for an example.

A plan file is a JavaScript file that produces a [ΛSON](YSON.md) object containing [product](Product.md) definitions.
Prebake combines the products from all the plan files to produce a [PlanGraph](PlanGraph.md) which is used to figure out how to build specific rules.

## Environment ##
All plan files are executed in an [environment](PlanFileEnv.md) that includes [common](CommonJsEnv.md) definitions and a `tools` object that contains a method for each available [tool](ToolFile.md).

## Return Conventions ##
The last statement in a plan file should produce a mapping of product names to product bodies.
The easiest way to do this is to just put a parenthesized [ΛSON](YSON.md) object at the end of your plan file, e.g.
```
({
  productName1: { actions: [tools.myTool(...)] },
  productName2: { actions: ... },
  productName3: ...
})
```