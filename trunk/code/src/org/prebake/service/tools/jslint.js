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

(function () {
  function empty() { return []; }

  function optionalArray(typeDescriptor) {
    return { type: 'default', delegate: typeDescriptor, defaultValue: empty };
  }

  var options = {
    type: 'Object',
    properties: {
      builtin: optionalArray({ type: 'Array', delegate: 'string' }),
      ignore: optionalArray({ type: 'Array', delegate: 'string' })
    }
  };

  var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });

  function decodeOptions(optionsSchema, action, opt_config) {
    // For this to be a mobile function we can't use schemaModule defined above.
    var schemaModule = load('/--baked-in--/tools/json-schema.js')(
        { load: load });
    var schemaOut = {};
    if (schemaModule.schema(optionsSchema).check(
            '_', action.options || {}, schemaOut, console,
            // Shows up in the error stack.
            [action.tool + '.action.options'])) {
      if (opt_config) { schemaModule.mixin(schemaOut._, opt_config); }
      return true;
    } else {
      return false;
    }
  }

  function flatten(var_args) {
    var out = [];
    var k = -1;
    function flattenOntoOut(args) {
      for (var i = 0, n = args.length; i < n; ++i) {
        var item = args[i];
        if (item instanceof Array) {
          flattenOntoOut(item);
        } else {
          out[++k] = item;
        }
      }
    }
    flattenOntoOut(arguments);
    return out;
  }

  return ({
    help: 'Sanity checks for JavaScript.\n<pre class=\"prettyprint lang-js\">'
        + schemaModule.example(schemaModule.schema(options)) + '</pre>',
    check: decodeOptions.bind({}, options),
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!decodeOptions(options, action, config)) { return os.failed; }
      var outDir = glob.rootOf(action.outputs);
      if (outDir === null) {
        console.error(
            'Cannot determine output directory from '
            + JSON.stringify(action.outputs));
        return os.failed;
      }
      return os.exec(flatten(
          'jslint', '--out', outDir,
          Array.map(config.builtin, function (s) { return ['--builtin', s ]; }),
          Array.map(config.ignore, function (s) { return ['--ignore', s ]; }),
          '--',
          inputs));
    }
  });
})()
