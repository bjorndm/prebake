# ΛSON <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/js/YSON.java'>(src)</a></font> #

Objects that are structurally similar to JSON but that allow embedding of code.

## Relationship to JSON ##

This is not a document format and is not meant for communication
between different trust domains.  Merely an object message format
useful within the same trust domain.

Anything representable in JSON is representable in ΛSON (module large
numbers that are not representable in JavaScript).

ΛSON extends the JSON object model to include [MobileFunction](MobileFunction.md)s :
JavaScript functions that can be moved from one JavaScript interpreter
to another without changing meaning.

## Structure ##
Like JSON, the format includes several primitive types
  * strings
  * booleans
  * numbers
  * `null`
several collection types
  * `[` serial arrays `]`
  * `{` associative arrays `}`
and adds an additional type
  * [MobileFunction](MobileFunction.md)s