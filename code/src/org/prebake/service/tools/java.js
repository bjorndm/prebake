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
  var toPath = function (sep, str) {
    return str.split(sep).filter(function (x) { return !!x; });
  }.bind({}, sys.io.path.separator);

  var options = {
    type: 'Object',
    properties: {
      className: { type: 'optional', delegate: 'string' },
      classpath: {
        type: 'optional',
        delegate: {
          type: 'union',
          options: [
            { type: 'string', xform: toPath },
            { type: 'Array', delegate: 'string' }
          ]
        }
      },
      asserts: {
        type: 'optional',
        delegate: {
          type: 'union',
          options: [
            // A minus sign at the beginning indicates disabled.
            'boolean', { type: 'Array', delegate: 'string' }
          ]
        }
      },
      systemAsserts: { type: 'optional', delegate: 'boolean' },
      verbose: {
        type: 'default',
        delegate: {
          type: 'union',
          options: [
            'boolean', { type: 'Array', delegate: ['class', 'gc', 'jni'] }
          ]
        },
        defaultValue: function () { return false; }
      },
      systemProps: {
        type: 'optional',
        // TODO: disallow '=' in property keys.
        delegate: { type: 'Object', properties: {},
                    doesNotUnderstand: 'string' }
      },
      vm: { type: 'optional', delegate: ['server', 'client'] },
      data: { type: 'optional', delegate: ['32', '64'] },
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
      // TODO: common -X options like memory management and GC type
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

  return ({
    help: ('Starts a java VM\n<pre class=\"prettyprint lang-js\">'
           + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
    check: decodeOptions.bind({}, options),
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!decodeOptions(options, action, config)) { return os.failed; }
      var extraClasspath = [];
      var className = config.className;
      var sources = [];
      for (var i = 0, n = inputs.length; i < n; ++i) {
        var input = inputs[i];
        var dot = input.lastIndexOf('.');
        switch (input.substring(dot + 1)) {
          case 'jar': extraClasspath.push(input); break;
          case 'class': break;
          default:
            // Remaining files can be substituted into the main method arg list.
            sources = inputs.slice(i);
            i = n;
            break;
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
      var classpath = Array.filter(
          config.classpath || extraClasspath,
          function (x) { return x && typeof x === 'string'; });
      var command = ['java'];
      var verbosity = config.verbose;
      if (verbosity) {
        if (verbosity === true) {
          command.push('-verbose');
        } else {
          for (var i = 0, n = verbosity.length; i < n; ++i) {
            command.push('-verbose:' + verbosity[i]);
          }
        }
      }
      var props = config.systemProps;
      if (props) {
        var hop = {}.hasOwnProperty;
        for (var k in props) {
          if (!hop.call(props, k)) { continue; }
          command.push('-D' + k + '=' + props[k]);
        }
      }
      if (config.vm) { command.push('-' + config.vm); }
      if (config.data) { command.push('-d' + config.data); }
      if (className) {
        command.push('-classpath', classpath.join(sys.io.path.separator));
      } else if (classpath.length >= 1 && /.jar$/.test(classpath[0])) {
        command.push('-jar', classpath[0]);
      } else {
        console.error(
            'Missing class name.  Make sure the first input is a jar with the'
            + ' Main-Class property in its manifest or use the className'
            + ' option.');
        return os.failed;
      }
      switch (config.asserts) {
        case true: command.push('-ea'); break;
        case false: case undefined: break;
        default:
          for (var i = 0, n = config.asserts.length; i < n; ++i) {
            var a = config.asserts[i];
            if (a[0] === '-') {
              command.push('-da:' + a.substring(1));
            } else {
              command.push('-ea:' + a);
            }
          }
          break;
      }
      if (config.systemAsserts) { command.push('-esa'); }
      if (className) { command.push(className); }
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