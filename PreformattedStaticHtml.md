# Preformatted Static HTML <font size='1'><a href='http://code.google.com/p/prebake/source/browse/trunk/code/src/org/prebake/core/PreformattedStaticHtml.java'>(src)</a></font> #

All in-JavaScript [documentation](DocumentationRecord.md) is written in a
subset of HTML4 that is not an XSS vector, and is easily converted to
plain text.

This HTML cannot contain scripts or event handlers, the CSS is
limited, and it is assumed to be inside a
`<body style="white-space: pre-wrap">` element so that newlines and
spaces are significant.

The HTML subset does include `<xmp>` and `<plaintext>` in case you
don't feel like escaping angle brackets.