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
  var options = {
    type: 'Object',
    properties: {
      args: {
        type: 'default',
        delegate: {
          type: 'Array',
          delegate: {
            type: 'union',
            options: ['string', { type: 'Array', delegate: 'nil' }]
          }
        },
        defaultValue: function () { return []; }
      }
    }
  };

  var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });

  function decodeOptions(optionsSchema, action, opt_config) {
    // For this to be a mobile function we can't use schemaModule defined above.
    var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });
    var schemaOut = {};
    var options = action.options || {};
    if (schemaModule.schema(optionsSchema).check(
            '_', options, schemaOut, console,
            // Shows up in the error stack.
            [action.tool + '.action.options'])) {
      if (opt_config) {
        schemaModule.mixin(schemaOut._, opt_config);
      }
      return true;
    } else {
      return false;
    }
  }

  return ({
    help: (
        'Executes the first input as an executable given arguments'
        + ' specified in options.  Any argument that is undefined is replaced'
        + ' with the remainder of the input list.\n'
        + '<pre class=\"prettyprint lang-js\">'
        + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
    check: decodeOptions.bind({}, options),
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!decodeOptions(options, action, config)) { return os.failed; }
      if (inputs.length === 0) {
        console.error('No inputs');
        return os.failed;
      }
      var command = [inputs[0]];
      var args = config.args;
      for (var i = 0, n = args.length; i < n; ++i) {
        var arg = args[i];
        if (typeof arg === 'string') {
          command[command.length] = arg;
        } else {  // The nil placeholder stands in for inputs.
          for (var j = 1, m = inputs.length; j < m; ++j) {
            command[command.length] = inputs[j];
          }
        }
      }
      return os.exec(command);
    }
  });
})()