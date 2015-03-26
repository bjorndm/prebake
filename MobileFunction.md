# Mobile Functions #

A JavaScript functions that can be moved from one JavaScript interpreter
to another without changing meaning.

A mobile function has no [free variables](http://en.wikipedia.org/wiki/Free_variables_and_bound_variables) besides commonly defined globals that are available in all contexts : `Object`, `Function`, `Date`, `Math`, etc.  See [the common JavaScript defs](CommonJsEnv.md) and <a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/js/YSON.java'>the source</a> for the full list.

For example, `function (x, y) { return x + y; }` is mobile because it has no free variables ; `x` and `y` are both parameters.

And `function (x) { return String(s); }` is mobile as well.  Even though it has a free variable `String`, that variable's behavior is specified by the language, so that function can be moved from one context to another without changing its meaning.

But `function (x, y) { foo(x + 1); }` is not mobile, because its meaning depends on the free variable `foo`.

## `mobileFunction.bind` ##
The EcmaScript 5 `bind` method works for mobile functions when called with [ΛSON](YSON.md) arguments.

So a mobile function curried with ΛSON arguments is a mobile function.  Also, a function whose only free variable is `this`, curried with ΛSON arguments is a mobile function.

```
// Not a mobile function since this is free.
// Math is free, but is bound to a builtin.
function hypotenuseMethod() {
  return Math.sqrt(this.x * this.x + this.y * this.y);
}
// A mobile function.  This is no longer free.
var boundHypotenuse = hypotenuseMethod.bind({ x: 3, y: 4 });
boundHypotenuse();  // -> 5

// A mobile function of 2 arguments
function hypotenuse(length, breadth) {
  return Math.sqrt(length * length + breadth * breadth);
}
// Still a mobile function but of zero arguments.
var zeroArgHypotenuse = hypotenuse.bind({}, 12, 9);
zeroArgHypotenuse();  // -> 15
```