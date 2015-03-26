# DocumentationRecord <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/core/Documentation.java'>(src)</a></font> #

Human readable documentation for part of the system.

It can be specified in two ways
  1. as a string of PreformattedStaticHtml
  1. as a JSON record with the structure shown below.

```
{
  "summary": "optional short text",
  "details": "required longer text",
  "contact": "optional email address <maintainer@example.org>"
}
```

## Summary ##
Short human readable documentation treated as PreformattedStaticHtml format.
If not provided, it is inferred from the details using the same scheme that [javadoc](http://java.sun.com/j2se/javadoc/writingdoccomments/) uses to find the first sentence:

> This sentence ends at the first period that is followed by a blank,
> tab, or line terminator, or at the first tag (as defined below).
> For example, this first sentence ends at "Prof.":

> This is a simulation of Prof. Knuth's MIX computer.

## Details ##
Human readable documentation string treated as PreformattedStaticHtml format.

## Contact ##
Email address of the maintainer who can answer questions.