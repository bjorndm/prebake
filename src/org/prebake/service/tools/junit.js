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
    listener: { type: 'optional', delegate: 'function' },
    test_class_filter: {
      type: 'default', delegate: 'string',
      defaultValue: function () { return '**'; }
    },
    report_dir: { type: 'optional', delegate: 'string' },
    // Classpath for tests if not apparent from inputs.
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
    },
    // The classpath including org.prebake.service.tools.ext.JUnitRunner
    // and its dependencies.
    runner_classpath: {
      type: 'optional',
      delegate: {
        type: 'union',
        options: [
          { type: 'string', xform: function (s) { return s.split(/[:;]/g); } },
          { type: 'Array', delegate: 'string' }
        ]
      }
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
  help: ('JUnit Test Runner.\n<pre class="prettyprint lang-js">'
         + schemaModule.example(schemaModule.schema(options)) + '</pre>'),
  check: decodeOptions.bind({}, options),
  fire: function fire(inputs, product, action, os) {
    var opt = {};
    if (!decodeOptions(options, action, opt)) {
      return {
        waitFor: function () { return -1; },
        run: function () { return this; }
      };
    }
    var testListener = opt.listener;
    var classpath = opt.classpath;
    var junitRunnerClasspath = opt.runner_classpath
        || java_classpath.slice();
    var extraClasspath = [];
    var testClasses = [];
    var testClassFilter = glob.matcher(opt.test_class_filter);
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var dot = input.lastIndexOf('.');
      if (dot >= 0) {
        switch (input.substring(dot)) {
          case '.jar': extraClasspath.push(input); break;
          case '.class':
            if (testClassFilter(input)) { testClasses.push(input); }
            break;
        }
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
    var pathSeparator = sys.io.path.separator;
    classpath = Array.filter(
        classpath
            .concat(junitRunnerClasspath)
            .concat(extraClasspath),
        function (x) { return !!x; })
        .join(pathSeparator);
    var wantsHtmlReport = false,
        wantsJsonReport = false,
        wantsXmlReport = false;
    var reportDir = opt.report_dir;
    var inferReportDir = reportDir === undefined;
    for (var i = 0, n = action.outputs.length; i < n; ++i) {
      var output = action.outputs[i];
      var dot = output.lastIndexOf('.');
      if (dot >= 0) {
        switch (output.substring(dot)) {
          case '.html': wantsHtmlReport = true; break;
          case '.json': wantsJsonReport = true; break;
          case '.xml':  wantsXmlReport = true; break;
          default: continue;
        }
        if (inferReportDir && reportDir === undefined) {
          // The report directory is the one that contains the json output dump
          // or index.html.
          var root = glob.rootOf(output);
          if (root) { reportDir = root; }
        }
      }
    }
    var reportTypes = Array.filter([
        wantsHtmlReport ? 'html' : null,
        wantsJsonReport ? 'json' : null,
        wantsXmlReport  ? 'xml'  : null],
        function (reportType) { return !!reportType; })
        .join(',');
    var command = [
        'java', '-classpath', classpath,
        'org.prebake.service.tools.ext.JUnitRunner',
        // testListener must be a mobile function if it came from externally.
        // TODO: make sure a bound mobile function can be serialized this way.
        typeof testListener === 'function' ? '' + testListener : '',
        reportDir || '', reportTypes]
        .concat(testClasses);
    var proc = os.exec.apply({}, command);
    var result;
    function OutProc() {
      this.waitFor = function () {
        result = proc.waitFor();
        // See JUnitRunner.Result enum.
        if (result === 0 || result === -1) {
          if (result) {
            console.warn('JUnit Tests failed');
          } else {
            console.log('JUnit Tests passed');
          }
        } else {
          console.warn('JUnit failed with result ' + result);
        }
        return result;
      };
    }
    OutProc.prototype = proc;
    return new OutProc;
  }
});
