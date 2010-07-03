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
  var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });

  var options = {
    type: 'Object',
    properties: {
      rename: {
        type: 'default',
        delegate: 'boolean',
        defaultValue: function () { return true; }
      }
    }
  };

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

  return ({
    help: 'Reduces JavaScript source code size.'
	+ '\n<pre class=\"prettyprint lang-js\">'
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
      var minGroups = {};
      var xform = glob.xformer(action.inputs, action.outputs);
      var errs = false;
      var hop = minGroups.hasOwnProperty;
      // Generate a mapping from inputs to outputs.  If one or more of the inputs
      // map to the same output, then minimize them together.
      // E.g. for
      //    tools.jsmin('src/foo/*.js', 'out/foo/*-min.js')
      // one file would be generated per input, but for
      //    tools.jsmin('src/foo/*.js', 'out/foo/bundle.js')
      // one file is generated which contains the concatenation of the minified
      // version of all the inputs.
      for (var i = 0, n = inputs.length; i < n; ++i) {
        var input = inputs[i];
        var output = xform(input);
        if (output == null) {
          console.error('Cannot determine output for input %s', input);
          errs = true;
        }
        if (!hop.call(minGroups, output)) { minGroups[output] = []; }
        minGroups[output].push(input);
      }
      if (errs) { return os.failed; }
      var flags = ['jsmin'];
      if (!config.rename) { flags.push('--norename'); }
      flags.push('--');
      var procs = [];
      for (var output in minGroups) {
        if (!hop.call(minGroups, output)) { continue; }
        procs.push(os.exec(flags.concat(minGroups[output])).writeTo(output));
      }
      switch (procs.length) {
        case 0: return os.passed;
        case 1: return procs[0];
        default:
          return {
            run: function () {
              for (var i = 0, n = procs.length; i < n; ++i) { procs[i].run(); }
              return this;
            },
            waitFor: function () {
              var result = 0;
              for (var i = 0, n = procs.length; i < n; ++i) {
                result |= procs[i].waitFor();
              }
              return result;
            }
          };
      }
    }
  });
})()
