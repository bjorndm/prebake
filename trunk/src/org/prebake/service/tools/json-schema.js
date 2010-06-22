// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview
 * Lets callers use a simple declarative syntax to both document
 * a configuration space, and validate that an actual configuration
 * object (specified as YSON) meets that space.
 *
 * <p>This module provides a single recursive operator:
 * {@code schema(typeDescriptor)} that returns a struct like
 * <code>({
 *  example: function (outBuf),
 *  check: function (key, value, out, console, stack) -> boolean })</code>
 *
 * <p>A typeDescriptor is one of:<ul>
 *   <li>A {@code typeof} string or the special value
 *     {@code uint32} or {@code int32}</li>
 *   <li>A pair like
 *     <code>{ type: typeDescriptor,
 *             xform: function (inValue) -> outValue }</code>
 *     which matches anything by type, but will output the result
 *     of the xform instead.
 *     If the outValue is undefined then it will not be included in
 *     the output at all.
 *   <li>An array of possible value like {@code ['foo', 'bar', 'baz']}.
 *   <li>A typeDescriptor union like
 *     <code>{ type: 'union', options: [typeDescriptor, ...] }</code>
 *   <li>A list matcher like
 *     <code>{ type: 'Array', delegate: typeDescriptor }</code>
 *     which produces an array when all the value's elements match the
 *     delegate.
 *   <li>An optional matcher like
 *     <code>{ type: 'optional', delegate: typeDescriptor }</code>
 *     which does nothing but succeeds if the value is undefined, or
 *     otherwise applies the delegate.
 *   <li>A default matcher like
 *     <code>{ type: 'default', delegate: typeDescriptor,
 *             defaultValue: function () -> value }</code>
 *     which succeeds using the function to produce a default value if the value
 *     is undefined, or otherwise applies the delegate.
 *   <li>An object matcher like
 *     <code>{ type: 'Object', properties: { key: typeDescriptor, ... },
 *             doesNotUnderstand: typeDescriptor }</code>
 *     where doesNotUnderstand is invoked for any property in the input not
 *     in properties.
 *   <li>A hand-crafted schema like
 *     <code>{ example: function (outBuf),
 *             check: function (key, value, out, console, stack) -> boolean
 *     }</code>
 * </ul>
 *
 * <p>The console passed to this module should support at least the
 * {@code warn} and {@code error} methods and optionally the {@code didYouMean}
 * method with the same signature as the global console.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @provides schema, hop, defaultSchema, setSchema, arraySchema,
 *     objectSchema, unionSchema, predicateSchema, renderExample
 * @requires JSON
 */

"use strict";

function schema(typeDescriptor) {
  if (typeof typeDescriptor === 'string') {
    switch (typeDescriptor) {
      case 'boolean': case 'number': case 'string': case 'undefined':
      case 'function': case 'object':
        return predicateSchema(
            typeDescriptor,
            function (x) { return typeof x === typeDescriptor; });
      case 'uint32':
        return predicateSchema(
            typeDescriptor, function (x) { return x === x >>> 0; });
      case 'int32':
        return predicateSchema(
            typeDescriptor, function (x) { return x === x >> 0; });
    }
  } else if (isArray(typeDescriptor)) {
    var optionMap = {};
    for (var i = 0, n = typeDescriptor.length; i < n; ++i) {
      var val = typeDescriptor[i];
      optionMap[JSON.stringify(val)] = Object.frozenCopy(val);
    }
    return setSchema(Object.freeze(optionMap));
  } else if (typeDescriptor instanceof RegExp) {
    return predicateSchema(
        '' + typeDescriptor,
        function (x) {
          return typeof x === 'string' && typeDescriptor.test(x);
        });
  } else if (typeof typeDescriptor === 'object') {
    if (typeof typeDescriptor.check === 'function'
        && typeof typeDescriptor.example === 'function') {
      return typeDescriptor;
    }
    if (typeof typeDescriptor.xform === 'function'
        && typeof typeDescriptor.type !== 'undefined') {
      return xformer(schema(typeDescriptor.type), typeDescriptor.xform);
    }
    switch (typeDescriptor.type) {
      case 'union':
        return unionSchema(typeDescriptor.options.map(schema));
      case 'Array':
        return arraySchema(schema(typeDescriptor.delegate));
      case 'Object':
        var propsOut = {};
        var props = typeDescriptor.properties;
        for (var key in props) {
          if (hop.call(props, key)) {
            propsOut[key] = schema(props[key]);
          }
        }
        var dnu = hop.call(typeDescriptor, 'doesNotUnderstand')
            ? schema(typeDescriptor['doesNotUnderstand'])
            : undefined;
        return objectSchema(propsOut, dnu);
      case 'optional':
        return defaultSchema(schema(typeDescriptor.delegate), function () {});
      case 'default':
        return defaultSchema(schema(typeDescriptor.delegate),
                             typeDescriptor.defaultValue);
    }
  }
  throw new Error('Bad type descriptor ' + JSON.stringify(typeDescriptor));
}

function unionSchema(options) {
  var schema = {
    check: function (key, value, out, console, stack) {
      var delayed = [];
      // Make a fake console so that if the second or subsequent options matches
      // we don't print spurious error messages from the first.
      var delayedConsole = {
        warn: function () {
          var args = arguments;
          delayed.push(function (c) { c.warn.apply(c, args); });
        },
        error: function () {
          var args = arguments;
          delayed.push(function (c) { c.error.apply(c, args); });
        },
        didYouMean: function () {
          var args = arguments;
          delayed.push(function (c) {
            if (c.didYouMean) { c.didYouMean.apply(c, args); }
          });
        }
      };
      for (var i = 0, n = options.length; i < n; ++i) {
        var dlen = delayed.length;
        // Assumes that failed attempts will not damage out.
        if (options[i].check(key, value, out, delayedConsole, stack)) {
          for (var j = dlen, m = delayed.length; j < m; ++j) {
            delayed[j](console);
          }
          return true;
        }
        // Make sure the warnings that show up are those from
        // the first option if none match.
        if (i !== 0) { delayed.length = dlen; }
      }
      console.warn(
          'Could not match any of ' + renderExample(schema) + ' for ' + stack);
      for (var i = 0, n = delayed.length; i < n; ++i) { delayed[i](console); }
      return false;
    },
    example: function (outBuf) {
      outBuf.push('(');
      for (var i = 0, n = options.length; i < n; ++i) {
        if (i) { outBuf.push('|'); }
        options[i].example(outBuf);
      }
      outBuf.push(')');
    }
  };
  return schema;
}

function arraySchema(delegate) {
  return {
    check: function (key, value, out, console, stack) {
      if (!isArray(value)) {
        console.error(
            'Expected an array, not ' + JSON.stringify(value)
            + ' for ' + stack);
        return false;
      }
      var outArr = [];
      var capture = {};
      var slen = stack.length;
      for (var i = 0, n = value.length; i < n; ++i) {
        stack[slen] = i;
        if (!delegate.check('_', value[i], capture, console, stack)) {
          stack.length = slen;
          return false;
        }
        outArr[i] = capture['_'];
      }
      out[key] = outArr;
      stack.length = slen;
      return true;
    },
    example: function (outBuf) {
      outBuf.push('[');
      delegate.example(outBuf);
      outBuf.push(',...', ']');
    }
  };
}

function objectSchema(props, dnu) {
  return {
    check: function (key, value, out, console, stack) {
      var outObj = {};
      if (!(value && outObj.toString.call(value) === '[object Object]')) {
        console.error(
            'Expected an object, not ' + JSON.stringify(value)
            + ' for ' + stack);
        return false;
      }
      var slen = stack.length;
      for (var vkey in value) {
        if (!hop.call(value, vkey)) { continue; }
        stack[slen] = vkey;
        if (hop.call(props, vkey)) { continue; }
        if (!(dnu && dnu.check(vkey, value[vkey], outObj, console, stack))) {
          stack.length = slen;
          var keyAndAllProps = [vkey];
          for (var pkey in props) {
            if (hop.call(props, pkey)) { keyAndAllProps.push(pkey); }
          }
          console.error('Unknown property ' + vkey + ' for ' + stack);
          console.didYouMean.apply(console, keyAndAllProps);
          return false;
        }
      }
      for (var pkey in props) {
        if (!hop.call(props, pkey)) { continue; }
        stack[slen] = pkey;
        var vProp = hop.call(value, pkey) ? value[pkey] : undefined;
        if (!props[pkey].check(pkey, vProp, outObj, console, stack)) {
          stack.length = slen;
          return false;
        }
      }
      out[key] = outObj;
      stack.length = slen;
      return true;
    },
    example: function (outBuf) {
      var sawOne = false;
      outBuf.push('{');
      for (var key in props) {
        if (!hop.call(props, key)) { continue; }
        if (sawOne) { outBuf.push(','); }
        sawOne = true;
        outBuf.push(JSON.stringify(key), ':');
        props[key].example(outBuf);
      }
      if (dnu) {
        if (sawOne) { outBuf.push(','); }
        outBuf.push('*', ':');
        dnu.example(outBuf);
      }
      outBuf.push('}');
    }
  };
}

function defaultSchema(delegate, defaultFactory) {
  return {
    check: function (key, value, out, console, stack) {
      if (value === undefined) {
        out[key] = defaultFactory();
        return true;
      }
      return delegate.check(key, value, out, console, stack);
    },
    example: function (outBuf) {
      outBuf.push('optional');
      delegate.example(outBuf);
    }
  };
}

function setSchema(options) {
  return {
    check: function (key, value, out, console, stack) {
      var str = JSON.stringify(value);
      if (hop.call(options, str)) {
        out[key] = options[str];
        return true;
      }
      console.warn('Illegal value ' + str + ' for ' + stack);
      if (console.didYouMean) {
        var strAndAllOptions = [str];
        for (var option in options) {
          if (hop.call(options, option)) {
            strAndAllOptions.push(option);
          }
        }
        console.didYouMean.apply(console, strAndAllOptions);
      }
      return false;
    },
    example: function (outBuf) {
      outBuf.push('(');
      var sawOne = false;
      for (var option in options) {
        if (hop.call(options, option)) {
          if (sawOne) { outBuf.push('|'); }
          sawOne = true;
          outBuf.push(option);
        }
      }
      outBuf.push(')');
    }
  };
}

function xformer(schema, xform) {
  var check = schema.check;
  return {
    check: function (key, value, out, console, stack) {
      if (check(key, value, out, console, stack)) {
        out[key] = xform(out[key]);
        return true;
      }
      return false;
    },
    example: schema.example
  };
}

/**
 * Given a schema, runs its {@code example} method and pretty prints the result.
 */
function renderExample(schema) {
  var tokens = [];
  schema.example(tokens);
  var out = [];
  var pendingSpace = false;
  var blankLine = false;
  var startWord = /^\w/, endWordOrColon = /[\w:]$/;
  var bracketStack = [];
  var blen = 0;
  for (var i = 0, k = -1, n = tokens.length; i < n; ++i) {
    var blankLineAfter = false;
    var tok = tokens[i];
    if (!blankLine && pendingSpace && startWord.test(tok)) {
      out[++k] = ' ';
    } else if (tok.length === 1) {
      switch (tok) {
        case '{':
          if (k) {
            out[++k] = '\n';
            for (var j = blen; --j >= 0;) { out[++k] = '  '; }
            blankLine = false;
          }
          blankLineAfter = true;
          // $FALL-THROUGH$
        case '[': case '(':
          bracketStack[blen++] = tok;
          break;
        case ')': case ']':
          if (blen) { --blen; }
          break;
        case '}':
          if (blen) { --blen; }
          blankLine = true;
          break;
        case ',':
          blankLineAfter = bracketStack[blen - 1] === '{';
          break;
      }
    }
    if (blankLine && k) {
      out[++k] = '\n';
      for (var j = blen; --j >= 0;) { out[++k] = '  '; }
    }
    out[++k] = tok;
    pendingSpace = endWordOrColon.test(tok);
    blankLine = blankLineAfter;
  }
  return out.join('');
}

function predicateSchema(typeDescriptor, predicate) {
  return {
    check: function (key, value, out, console, stack) {
      if (predicate(value)) {
        out[key] = value;
        return true;
      }
      console.error(
          'Expected ' + typeDescriptor + ', not ' + JSON.stringify(value)
          + ' for ' + stack);
      return false;
    },
    example: function (outBuf) { outBuf.push(typeDescriptor); }
  };
}

var hop = {}.hasOwnProperty;
var str = {}.toString;

function isArray(o) {
  // TODO Figure out why instanceof Array doesn't work.
  return o instanceof Array || str.call(o) === '[object Array]';
}

function mixin(from, to) {
  for (var k in from) {
    if (hop.call(from, k)) { to[k] = from[k]; }
  }
}

// Export schema from this module.
({
  schema: schema,
  example: renderExample,
  mixin: mixin
});
