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

var options = {
  type: 'Object',
  properties: {
    operation: { type: 'optional', delegate: ['c', 't', 'x'] },
    manifest: {
      type: 'optional',
      delegate: { type: 'Object', properties: {}, doesNotUnderstand: 'string' }
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

({
  help: (
      'Pack or unpack a java Archive.\n<pre class=\"prettyprint lang-js\">'
      + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
  check: decodeOptions.bind({}, options),
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!decodeOptions(options, action, config)) { return os.failed; }
    var operation = config.operation;
    var jarfile;
    var manifest = config.manifest;
    if (operation === 'x'
        || (!operation && inputs.length === 1
            && /\.([jw]ar|zip)$/.test(inputs[0]))) {
      operation = 'x';
      jarfile = inputs[0];
    } else if (action.outputs.length === 1) {
      if (operation === undefined) { operation = 'c'; }
      try {
        jarfile = glob.xformer('foo', action.outputs[0])('foo');
      } catch (ex) {
        throw new Error(
            "Expected a fully specified archive, not " + action.outputs[0]
            + ".  If you are trying to extract, specify the option"
            + " { operation: 'x' }.");
      }
    } else {
      throw new Error('Cannot determine whether to jar or unjar');
    }

    var command = ['$$jar', operation || 'c', jarfile];
    if (operation === 'x') {
      // Channel outputs based on root paths.
      // E.g. the output foo///**.bar means put all **.bar files under the foo
      // directory.
      for (var i = 0, n = action.outputs.length; i < n; ++i) {
        command.push(action.outputs[i]);
      }
    } else {
      if (manifest) {
        command.push(0);
        var manifestStart = command.length;
        var hop = {}.hasOwnProperty;
        for (var k in manifest) {
          if (!hop.call(manifest, k)) { continue; }
          command.push(k, String(manifest[k]));
        }
        command[manifestStart - 1] = String(command.length - manifestStart);
      } else {
        command.push('-1');
      }
      for (var i = 0, n = action.inputs.length; i < n; ++i) {
        var inputGlob = action.inputs[i];
        var matching = Array.filter(inputs, glob.matcher(inputGlob));
        if (matching.length) {
          var sourceDir = glob.rootOf(inputGlob);
          var xform = glob.xformer(sourceDir.replace(/\\/g, '/') + '/**', '**');
          command.push(sourceDir, String(matching.length));
          for (var j = 0, m = matching.length; j < m; ++j) {
            command[command.length] = xform(matching[j]);
          }
        }
      }
    }
    return os.exec(command);
  }
});
