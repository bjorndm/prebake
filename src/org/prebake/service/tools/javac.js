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
    classpath: {
      type: 'optional',
      delegate: {
        type: 'union',
        options: [
          { type: 'string', xform: function (s) { return s.split(/[:;]/g); } },
          { type: 'Array', delegate: 'string' }
        ]
      }
    },
    d: { type: 'optional', delegate: 'string' },
    nowarn: { type: 'optional', delegate: 'boolean' },
    g: {
      type: 'optional',
      delegate: {
        type: [true, false, 'none', 'vars', 'source', 'lines'],
        xform: function (v) {
          return typeof v === 'string' ? ':' + v : v ? '' : ':none';
        }
      }
    },
    source: { type: 'optional', delegate: /^\d+(?:\.\d+)?$/ },
    target: { type: 'optional', delegate: /^\d+(?:\.\d+)?$/ },
    Xlint: {
      type: 'optional',
      delegate: {
        type: 'union',
        options: [
          {
            type: 'Array',
            delegate: [
              'cast', 'deprecation', 'divzero', 'empty', 'unchecked',
              'fallthrough', 'path', 'serial', 'finally', 'overrides',
              '-cast', '-deprecation', '-divzero', '-empty', '-unchecked',
              '-fallthrough', '-path', '-serial', '-finally', '-overrides'
            ]
          },
          ['all', 'none', '' /* recommended */]
        ]
      } 
    }
  }
};

var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });

function decodeOptions(optionsSchema, action, opt_config) {
  // Fot this to be a mobile function we can't use schemaModule defined above.
  var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });
  var schemaOut = {};
  var options = action.options || {};
  if (schemaModule.schema(optionsSchema).check(
          '_', options, schemaOut, console,
          // Shows up in the error stack.
          [action.tool + '.action.options'])) {
    if (schemaOut._.d === undefined) {
      var outDir = glob.rootOf(action.outputs);
      if (!outDir) {
        if (outDir === null) {
          console.error(
              'Cannot determine output directory for class files.'
              + '  Please include the same tree root in all your output globs.'
              + '  E.g., "lib///**.class"');
          return false;
        } else {
          console.warn(
              'Putting class files in same directory as source files.'
              + '  Maybe include a tree root in your output globs.'
              + '  E.g., "lib///**.class"');
          outDir = undefined;
        }
      }
      schemaOut._.d = outDir;
    }
    if (opt_config) {
      schemaModule.mixin(schemaOut._, opt_config);
    }
    return true;
  } else {
    return false;
  }
}

({
  help: 'Java compiler.\n' + schemaModule.example(schemaModule.schema(options)),
  check: decodeOptions.bind({}, options),
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!decodeOptions(options, action, config)) {
      return {
        waitFor: function () { return -1; },
        run: function () { return this; }
      };
    }
    var extraClasspath = [];
    var sources = [];
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var dot = input.lastIndexOf('.');
      switch (input.substring(dot + 1)) {
        case 'jar': extraClasspath.push(input); break;
        case 'class': break;
        // java, jsp, or any input to an annotation processor.
        default: sources.push(input); break;
      }
    }
    for (var i = 0, n = action.inputs.length; i < n; ++i) {
      var input = action.inputs[i];
      var dot = input.lastIndexOf('.');
      switch (input.substring(dot + 1)) {
        case 'class':
          var classDir = glob.rootOf(input);
          if (classDir) { extraClasspath.push(classDir); }
          break;
      }
    }
    var classpathStr = (config.classpath || extraClasspath)
        .filter(function (x) { return x && typeof x === 'string'; })
        .join(sys.io.path.separator);
    var command = ['javac', '-Xprefer:source'];
    if (typeof config.d === 'string') { command.push('-d', config.d); }
    if (classpathStr) { command.push('-classpath', classpathStr); }
    if (typeof config.g === 'string') { command.push('-g' + config.g); }
    if (config.source) { command.push('-source', config.source); }
    if (config.target) { command.push('-target', config.target); }
    if (config.nowarn) { command.push('-nowarn'); }
    if (config.Xlint) {
      if (config.Xlint.length) {
        command.push('-Xlint:' + config.Xlint);
      } else {
        command.push('-Xlint' + config.Xlint);
      }
    }
    command = command.concat(sources);
    return os.exec.apply({}, command);
  }
});
