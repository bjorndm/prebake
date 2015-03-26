# Glob <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/core/Glob.java'>(src)</a></font> #
A glob is a pattern used to specify a group of files.

## Meaning ##
There are many different glob syntaxes used on various systems.

| **Special Character** | **Meaning** |
|:----------------------|:------------|
| `*` | As in most glob syntaxes, `*` matches any number of characters other than a path separator. |
| `**` | As in ANT, `**` matches any number of characters including path separators. |
| `/` | The `/` character matches a file separator regardless of operating system.  So it matches a `/` on `*`NIX systems and `\` on Windows. |


## Examples ##
| **Glob** | **Matches** | **No Match** |
|:---------|:------------|:-------------|
| `*.cpp` | `foo.cpp` | `foo.h`, `foo/bar.cpp` |
| `**.cpp` | `foo.cpp`, `foo/bar.cpp` | `foo.h` |
| `foo/*.js` | `foo/bar.js` | `foo.js`, `bar/foo.js` |
| `foo/*` | `foo/bar`, `foo/bar.py` | `foo` |
| `foo/**/*.rb` | `foo/bar.rb`, `foo/bar/baz/boo.rb` | `foo.rb` |


## Matching and Substitution ##
A glob can be thought of as a [quasi-literal](http://www.cypherpunks.to/erights/elang/grammar/quasi-overview.html) for file paths ; it can provide both match and substitute operators.

The ability to match and substitute can come in handy when trying to map one file tree to another.  For example, in
```
  tools.cp('src/**/stuff/*.ext1', 'out/**/*.ext2');
```
defines an action that copies all files that end with `.ext1` that are children of a `stuff` directory under source, to a corresponding directory under `out` but without the `stuff` path element and with a different file extension.

The `cp` tool figures out the destination of each output file by substituting quasi-holes.  Consider applying the above action to the input file `src/foo/bar/stuff/baz.ext1`:

| input path | `src` | `/` | `foo/bar` | `/` | `stuff` | `/` | `baz` | `.ext1` |
|:-----------|:------|:----|:----------|:----|:--------|:----|:------|:--------|
| input glob | `src` | `/` | `**` |      `/` | `stuff` | `/` | `*` |  `.ext1` |
| output glob | `out` | `/` | `**` | `/` |  |  |  `*` |   `.ext2` |
| output path | `out` | `/` | `foo/bar` | `/` |  |  | `baz` | ` .ext2` |

Tools can do this kind of substitution by using the [common](CommonJsEnv.md) `glob.matcher` JavaScript API.

There is a richer version of matching that allows depends on named holes.  If you need to produce documentation in a variety of languages, you might write a rule using a named hole `*(locale)` like:

| input globs | `docs/translation-bundles/*(locale).xml` , `docs/en/**.html` |
|:------------|:-------------------------------------------------------------|
| output globs | `docs/*(locale)/**.html` |

But the relationship between inputs and outputs can't be inferred until you know what `locale` is.
If a developer wants to build `docs/es/index.html`, then we can infer that `locale` is `es` (the Spanish language locale identifier) by matching and then substitute that value into the inputs to come up with a rule:

| input globs | `docs/translation-bundles/es.xml` , `docs/en/**.html` |
|:------------|:------------------------------------------------------|
| output globs | `docs/es/**.html` |


## Constraints ##
To allow us to efficiently compute intersections, we place some limits on where `*` and `**` can appear.
  * three or more asterisks (`***`) cannot appear in a row.
  * both `*` and `**` must appear either at the beginning or directly after a path separator (`/`).  It is possible to do a suffix match of a path element but not a prefix match.
  * the special path elements `.` and `..` cannot appear in a glob.
  * two path separators cannot appear adjacently as in `foo//bar`

See the grammar below for a formal description.


## Intersection ##
The PreBakery infers dependencies between [Product](Product.md)s when there is a non-empty [intersection](http://en.wikipedia.org/wiki/Intersection_(set_theory)) between the outputs of one product and the inputs of the other.

Globs specify a regular language and can be expressed in terms of regular expressions, with `*` translated to `[^/]*`, and `**` translated to `.*`, and every other non-alphanumeric character translated to `\` followed by that character with a few tweaks to deal with empty matches bracketed by path separators.  E.g. the glob `foo/**/*Test.java` translates to `^foo/(?:.+/)?[^/]*Test\.java$` in perl5 syntax.

Two globs intersect if (and only if) there exists at least one string that is in both languages.  E.g., `foo/*/baz.js` and `*/bar/*` both have `foo/bar/baz.js` in common.  But `*.java` and `*.class` do not.

The intersection of two globs can always be expressed as a glob.


## TreeRoot ##
A glob can contain a tree root that [tools](ToolFile.md) use to determine where a file tree starts.  The tree root is the portion of the glob before the `///`.  Tools treat tree roots differently but here are some examples.

| **Tools** | **Glob** | **Meaning** |
|:----------|:---------|:------------|
| `gcc` | input `foo/bar///baz/**.h` | `foo/bar` should be an include (-I) directory |
| `javac`, `javadoc` | input `project/src///com/foo/**.java` | `project/src/` is part of the source-path |
| `javac`, `junit` | input `lib///**.class` | `lib` is on the class-path |
| `javac` | output `lib///**.class` | `lib` is the output directory |
| `tar`, `jar` | input `foo/bar///baz/*.txt` | `foo/bar` should be the root of the archive being created |


## Grammar ##
```
Glob
  : OptPattern
  | '/' OptPattern
  | TreeRoot '///' OptPattern
  ;

TreeRoot
  : empty
  | PatternNoStar
  ;

OptPattern
  : empty
  | Patterm
  ;

Pattern
  : PathElement PatternTail
  ;

PatternTail
  : empty
  | '/' Pattern
  ;

PatternNoStar
  : PathElementNoStar PatternTailNoStar
  ;

PatternTailNoStar
  : empty
  | '/' PatternNoStar
  ;

PathElement
  : Hole OptQuasiName PathElementNoStar
  | PathElementNoStar
  ;

OptQuasiName
  : empty
  | '(' JsIdentifier ')'
  ;

PathElementNoStar
  : NoDot PatternNoStar
  | '.' PathElement
  | '.' '.' '.' Dots
  ;

Dots
  : empty
  | '.' Dots
  | PathElement
  ;

Hole
  : '*'
  | '**'
  ;

PatternChar
  ~ any except '/', '*', '(', ')', '{', '}'
  ;

NoDot
  ~ PatternChar except '.'
  ;

OptStar
  : empty
  | '*'
  ;
```