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
    'effort': { type: 'optional', delegate: ['min', 'default', 'max'] },
    'priority': { type: 'optional', delegate: ['low', 'medium', 'high'] },
    'relaxed': { type: 'default', delegate: 'boolean',
                 defaultValue: function () { return false; } },
    classpath: {
      type: 'default',
      delegate: {
        type: 'union',
        options: [
          { type: 'string', xform: function (s) { return s.split(/[:;]/g); } },
          { type: 'Array', delegate: 'string' }
        ]
      },
      defaultValue: function () { return []; }
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
    if (opt_config) {
      schemaModule.mixin(schemaOut._, opt_config);
    }
    return true;
  } else {
    return false;
  }
}

({
  help: ('Runs FindBugs to find common problems in Java source code\n'
         + schemaModule.example(schemaModule.schema(options))),
  check: decodeOptions.bind({}, options),
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!decodeOptions(options, action, config)) {
      return {
        run: function () { return this; },
        waitFor: function () { return -1; }
      };
    }
    var pathSeparator = sys.io.path.separator;
    var extraClasspath = [];
    var sources = [];
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var dot = input.lastIndexOf('.');
      switch (input.substring(dot + 1)) {
        case 'class': sources.push(input); break;
        case 'jar': extraClasspath.push(input); break;
        // TODO: custom XSL stylesheets
      }
    }
    var outputFile;
    var outputTypeFlag;
    for (var i = 0, n = action.outputs.length; i < n; ++i) {
      var output = action.outputs[i];
      var dot = output.lastIndexOf('.');
      var ext = output.substring(dot + 1);
      switch (ext) {
        case 'html': case 'xml': outputTypeFlag = ext; break;
        case 'xdoc': outputTypeFlag = 'xdocs'; break;
      }
      if (outputTypeFlag) {
        try {
          outputFile = glob.xformer('foo', output)('foo');
        } catch (ex) {
          console.error('Cannot determine output file : ' + ex.message);
          return {
            run: function () { return this; },
            waitFor: function () { return -1; }
          };
        }
        break;
      }
    }
    if (!outputFile) {
      console.error(
          'No output file.  Please specify an output with an'
          + ' .html, .xml, or .xdoc extension.');
      return {
        run: function () { return this; },
        waitFor: function () { return -1; }
      };
    }
    var classpath = config.classpath;
    if (extraClasspath.length) {
      classpath = classpath.concat(extraClasspath);
    }
    var command = ['findbugs', '-textui', '-progress'];
    if (config.effort) { command.push('-effort:' + config.effort); }
    if (config.priority) { command.push('-' + config.priority); }
    if (config.relaxed) { command.push('-relaxed'); }
    command.push('-' + outputTypeFlag, '-output', outputFile);
    if (classpath.length) {
      command.push('-auxclasspath', classpath.join(pathSeparator));
    }
    command = command.concat(sources);

    var proc = os.exec.apply({}, command);
    function OutProc() {
      this.waitFor = function () {
        var result = proc.waitFor();
        if (result === 0 || result === 1) {
          if (result) {
            console.log('See findbugs report at ' + outputFile);
          }
          return 0;
        } else {
          console.warn('Findbugs failed with status code ' + result);
          return result;
        }
      };
    }
    OutProc.prototype = proc;
    return new OutProc;
  }
});
