# Plan Graph <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/plan/PlanGraph.java'>(src)</a></font> #

Prebake uses a graph of dependencies between products to figure out how to build targets.

If one [product's](Product.md) outputs [intersect](Glob.md) with another product's inputs, then the former product needs to be up-to-date before the latter can be built.  For example, if product `classes` has outputs `lib/**.class` and product `jars` has inputs `libs/**.class`, then `classes` has to be built before `jars`.  All product dependencies are inferred this way.  Unlike ANT and Make, there are no explicit dependency declarations between products.

The `graph` [command](PreBakeCommand.md) dumps the plan graph to GraphViz format.