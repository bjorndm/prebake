The ignore [flag](Usage.md) passed to the PreBakery causes it to overlook certain files.  Paths that match the ignore regular expression.

The regular expression is specified in [Java's variant of Perl 5 syntax](http://java.sun.com/javase/7/docs/api/java/util/regex/Pattern.html).

## Example ##
The ignore pattern
```
\.pyc$|~$|/\.svn/|\.rej$|\.r\d+$
```
will ignore compiled python files generated from python source files, temporary files left by emacs, subversion's internal book-keeping files, and merge conflict files left by patch.

## Affects ##
The ignore pattern affects several parts of the system.
  1. ignored files will not be copied to the TmpWorkspace so cannot be [SourceFile](SourceFile.md)s and will not be visible to [BuildAction](BuildAction.md)s
  1. ignored files will not be copied back from the TmpWorkspace so cannot be [GeneratedFile](GeneratedFile.md)s.

Ignored files can be explicit inputs to the system, so can be
  1. [PlanFile](PlanFile.md)s
  1. [ToolFile](ToolFile.md)s
  1. `load`ed by the above

## OS Independent ##
The ignored files will match against paths normalized to use POSIX style file separators so `/` can be used as the file systems even on Windows systems.

## The default ignore pattern ##
If the ignore pattern is not specified, it is the union of the below.

| **Regexp** | **Reason** |
|:-----------|:-----------|
| `\.pyc$` | cached compiled Python |
| `\.elc$` | cached compiled ELISP |
| `\.bak$` | other backup files |
| `\.rej$` | `patch` reject file |
| `\.prej$` | more reject files |
| `\.tmp$` | common temp file suffix |
| `~$` | emacs backup |
| `/#[^/]+#$` | emacs unsaved |
| `/%[^/]*%$` | more backup files  |
| `/\.(#+|_+)$` | temporary files |
| `/(CVS|SCCS|\.svn)(/|$)` | revision control bookkeeping files |
| `/\.cvsignore$` | CVS metadata |
| `/\.gitignore$` | GIT metadata |
| `/vssver.scc$` | more revision control metadata |
| `/\.DS_Store$` | Macintosh droppings |

This is based, in part, on ANT's [default excludes](http://ant.apache.org/manual/dirtasks.html).