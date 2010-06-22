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

function yieldFalse() { return false; }
function yieldNone() { return []; }

var options = {
  type: 'Object',
  properties: {
    dir: { type: 'optional', delegate: 'string' },
    g: { type: 'default', delegate: 'boolean', defaultValue: yieldFalse },
    message_source: { type: 'optional', delegate: 'string' },
    output_language: {
      type: 'optional',
      delegate: {
        type: 'Array',
        delegate: ['cpp', 'cpp_header', 'java', 'js', 'xmb']
      }
    },
    output_properties: { type: 'optional', delegate: 'boolean' },
    schema: {
      type: 'default',
      delegate: { type: 'Array', delegate: 'string' },
      defaultValue: yieldNone
    },
    source: { type: 'optional', delegate: 'string' },
    verbose: { type: 'default', delegate: 'boolean', defaultValue: yieldFalse },
    error: {
      type: 'default',
      delegate: { type: 'Array', delegate: ['i18n'] },
      defaultValue: yieldNone
    },
    warn: { type: 'optional', delegate: ['i18n', 'error'] }
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
    var decoded = schemaOut._;
    if (decoded.dir === undefined) {
      var dir = glob.rootOf(action.outputs);
      if (dir === null) {
        console.error(
            'Cannot determine output directory.  Please specify a tree root'
            + ' on output globs or use the "out" option');
        return false;
      }
      decoded.dir = dir;
    }
    if (decoded.output_language === undefined) {
      var outputLangs = {};
      var hop = outputLangs.hasOwnProperty;
      for (var i = 0, n = action.outputs.length; i < n; ++i) {
        var output = action.outputs[i];
        var ext = output.substring(output.lastIndexOf('.') + 1);
        switch (ext) {
          case 'cc': case 'cpp': outputLangs.cpp = true; break;
          case 'h': outputLangs.cpp_header = true; break;
          case 'java': outputLangs.java = true; break;
          case 'js': outputLangs.js = true; break;
          case 'xmb': outputLangs.xmb = true; break;
        }
      }
      var outputLangList = [];
      for (var k in outputLangs) {
        if (hop.call(outputLangs, k)) { outputLangList.push(k); }
      }
      if (outputLangList.length) {
        decoded.output_language = outputLangList;
      } else {
        console.error(
            'Cannot determine output languages.'
            + '  Maybe specify an extension on your output globs'
            + ' or use the "output_language" option');
        return false;
      }
    }
    if (decoded.source === undefined) {
      var gxpGlobs = [];
      for (var i = 0; i < n; ++i) {
        if (/\.gxp$/.test(action.inputs[i])) {
          gxpGlobs.push(action.inputs[i]);
        }
      }
      if (gxpGlobs.length) { decoded.source = glob.rootOf(gxpGlobs); }
    }
    if (opt_config) {
      schemaModule.mixin(decoded, opt_config);
    }
    return true;
  } else {
    return false;
  }
}

({
  help: ('Compiles GXP templates to Java, JavaScript, or C.\n<pre>'
         + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
  check: decodeOptions.bind({}, options),
  fire: function fire(inputs, product, action, os) {
    var opt = {};
    if (!decodeOptions(options, action, opt)) { return os.failed; }
    if (inputs.length === 0) { return os.passed; }
    var command = ['gxpc', '--dir', opt.dir];
    if (opt.g) { command.push('--g'); }
    if (opt.message_source) { command.push(opt.message_source); }
    for (var i = 0, n = opt.output_language.length; i < n; ++i) {
      command.push('--output_language', opt.output_language[i]);
    }
    if (opt.output_properties) { command.push('--output_properties'); }
    for (var i = 0, n = opt.schema.length; i < n; ++i) {
      command.push('--schema', opt.schema[i]);
    }
    if (opt.source !== undefined) { command.push('--source', opt.source); }
    if (opt.verbose) { command.push('--verbose'); }
    for (var i = 0, n = opt.error.length; i < n; ++i) {
      command.push('--error', opt.error[i]);
    }
    if (opt.warn) { command.push('--warn', opt.warn); }
    return os.exec(command.concat(inputs));
  }
});
