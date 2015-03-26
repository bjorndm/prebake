# The `bake` client <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/client/Bake.java'>(src)</a></font> #

## Usage ##

```
bake [-v | -vv | -q | -qq | --logLevel=<level>] <command> <arg0>...

    -qq         extra quiet : errors only
    -q          quiet : warnings and errors only
    -v          verbose
    -vv         extra verbose
    --logLevel  see java.util.logging.Level for value names
```

Where the arguments are specific to the [command](PreBakeCommand.md) used.

| **command line** | **effect** |
|:-----------------|:-----------|
| `bake bake cake` | to build the target named cake |
| `bake shutdown` | shuts the service down |