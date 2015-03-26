# Commands #

The [bake](Bake.md) client sends commands to the PreBakery over a [port](PrebakeDirectory.md).

Commands are strings of JSON and the structures below is in the [JSON ML](http://jsonml.org/) subset of JSON.

E.g. ` [ 'build', {}, [ 'target0', 'target1' ] ] ` to request that the service kick off a build of targets `target0` and `target1`.

Each command has the form ` [`_verb_`, {"`_option_`": `_value_` }, [ `_arguments_` ] ]`.

## Verbs ##
| **Verb** | **Options** | **Arguments** | **Effect** |
|:---------|:------------|:--------------|:-----------|
| handshake | N/A | token from PrebakeDirectory | establishes authority to issue commands |
| bake | N/A | targets | builds the given targets |
| auth\_www | N/A | uri path | emits a URL that can be used to authenticate a browser session to the docs server |
| files\_changed | N/A | paths | so IDEs can push changes to source files |
| graph | N/A | targets | prints, in [DOT](http://www.graphviz.org/doc/info/lang.html) format, a graph of the dependencies between the given targets and all targets that any of them indirectly depend upon. |
| plan | N/A | targets | prints the targets that would be built if targets were passed to build |
| shutdown | N/A | N/A | shuts down the PreBakery |
| sync | N/A |  | wait until all the queue of changed files is empty |
| tool\_help | N/A | N/A | displays info about the tools available |