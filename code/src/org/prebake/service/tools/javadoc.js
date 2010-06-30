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
      d: { type: 'optional', delegate: 'string' },
      link: { type: 'optional', delegate: { type: 'Array', delegate: 'string' } },
      header: { type: 'optional', delegate: 'string' },
      footer: { type: 'optional', delegate: 'string' },
      top: { type: 'optional', delegate: 'string' },
      bottom: { type: 'optional', delegate: 'string' },
      visibility: {
        type: 'optional', delegate: ['private', 'package', 'protected']
      },
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
    // For this to be a mobile function we can't use schemaModule defined above.
    var schemaModule = load('/--baked-in--/tools/json-schema.js')(
        { load: load });
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
    help: ('Builds HTML Java API documentation from java source files.\n'
           + '<pre class="prettyprint lang-js">'
           + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
    check: decodeOptions.bind({}, options),
    fire: function fire(inputs, product, action, os) {
      var opt = {};
      if (!decodeOptions(options, action, opt)) { return os.failed; }
      var outDir = opt.d;
      if (typeof outDir !== 'string') {
        outDir = glob.rootOf(action.outputs);
        if (typeof outDir !== 'string') {
          throw new Error(
              'Could not infer documentation root from ' + action.inputs);
        }
      }
      var pathSeparator = sys.io.path.separator;
      var extraClasspath = [];
      var sourcePath = [];
      var sources = [];
      for (var i = 0, n = inputs.length; i < n; ++i) {
        var input = inputs[i];
        var dot = input.lastIndexOf('.');
        switch (input.substring(dot + 1)) {
          case 'jar': extraClasspath.push(input); break;
          case 'class': break;
          default: sources.push(input); break;  // java, jsp, etc.
        }
      }
      var endsWithClass = /.class$/;
      var endsWithJava = /.java$/;
      for (var i = 0, n = action.inputs.length; i < n; ++i) {
        var input = action.inputs[i];
        if (endsWithClass.test(input)) {  // E.g. lib/**.class
          extraClasspath.push(glob.rootOf(input));
        } else if (endsWithJava.test(input)) {
          sourcePath.push(glob.rootOf(input));
        }
      }
      var classpath = opt.classpath;
      if (extraClasspath.length) {
        classpath = classpath.concat(extraClasspath);
      }
      var classpathStr = Array.filter(
          classpath, function (x) { return x && typeof x === 'string'; })
          .join(sys.io.path.separator);
      var command = ['javadoc', '-d', outDir, '-quiet'];
      if (classpathStr) { command.push('-classpath', classpathStr); }
      if (opt.link) {
        for (var links = opt.link, i = 0, n = links.length; i < n; ++i) {
          command.push('-link', links[i]);
        }
      }
      if (sourcePath.length) {
        command.push('-sourcepath', sourcePath.join(pathSeparator));
      }
      if (typeof opt.header === 'string') { command.push('-header', opt.header); }
      if (typeof opt.footer === 'string') { command.push('-footer', opt.footer); }
      if (typeof opt.top === 'string') { command.push('-top', opt.top); }
      if (typeof opt.bottom === 'string') { command.push('-bottom', opt.bottom); }
      if (opt.visibility) { command.push('-' + opt.visibility); }
      command = command.concat(sources);
      return os.exec(command);
    }
  });
})()
