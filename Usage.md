# Usage #

## The prebakery [Service](PreBakery.md) ##
```
prebakery --root=<client-root-directory> [<logging-options>] [<options>] [<plan-file> ...]
```

will start up the bakery service.  It will write create a `.prebake` [directory](PrebakeDirectory.md) if there is none already.

| `--root=<dir>` | a [directory](ClientRoot.md) containing all [SourceFile](SourceFile.md)s | |
|:---------------|:-------------------------------------------------------------------------|:|
| `--ignore=<ignore-pattern>` | a regex of file paths to ignore | E.g. `\.bak$|/\.svn/` ignores files that end with ".bak" and ".svn" directories and their contents. |
| `--umask=<umask>` | [mask](http://en.wikipedia.org/wiki/Umask) for permissions on all built files | In octal |
| `--tools=<dir0>:<dir1>` | a search path for [tools](ToolFile.md) |  |
| `--www-port` | a port number | for serving [HTTP](http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/www/package-info.java) documentation, status, and logs |
| `-localhost-trusted` | boolean | if specified, then the HTTP requests on `--www-port` don't need credentials |
| `<plan-file>` | paths to [plan](PlanFile.md) files. | If none supplied, defaults to `<client-root-directory>/Bakefile.js` |

## The bake [client](Bake.md) ##
```
bake [<logging-options>] [<verb>] <target> ...
```

will connect to a `prebakery` by looking for a `.prebake` [directory](PrebakeDirectory.md) in an ancestorward traversal and send the specified [command](PreBakeCommand.md).  If no verb is specified, then it defaults to `bake`.

## Commands ##
Both the [service](http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/service/Main.java) and the [client](http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/client/Main.java) are implemented as Java classes.
The are executable scripts under the `bin`
[directory](http://code.google.com/p/prebake/source/browse/trunk/code/bin/)
that can be used to launch these in a JVM.