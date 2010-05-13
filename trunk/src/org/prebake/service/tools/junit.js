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

({
  help: 'JUnit Test Runner',
  check: function (action) {
    // TODO require the test_class_filter
    glob.matcher(action.options.test_class_filter);
  },
  fire: function fire(inputs, product, action, os) {
    var opts = action.options;
    // TODO: JVM system properties
    function opt(name, opt_defaultValue) {
      if ({}.hasOwnProperty.call(opts, name)) {
        return opts[name];
      } else {
        return opt_defaultValue;
      }
    }
    function cpOpt(name, defaultValue) {
      var cp = opt(name);
      if (cp) {
        if (typeof cp === 'object' && cp.length === (cp.length >>> 0)) {
          return cp;
        } else {
          return String(cp).split(pathSeparator);
        }
      }
      return defaultValue.split(pathSeparator);
    }
    var testListener = opt('listener');
    var pathSeparator = sys.io.path.separator;
    // Classpath for tests if not apparent from inputs.
    var classpath = cpOpt('classpath', '');
    // The classpath including org.prebake.service.tools.ext.JUnitRunner
    // and its dependencies.
    var junitRunnerClasspath = cpOpt('runner_classpath', java_classpath);
    var extraClasspath = [];
    var testClasses = [];
    var testClassFilter = glob.matcher(action.options.test_class_filter);
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
    classpath = Array.filter(
        classpath
            .concat(junitRunnerClasspath)
            .concat(extraClasspath),
        function (x) { return !!x; })
        .join(pathSeparator);
    var wantsHtmlReport = false,
        wantsJsonReport = false,
        wantsXmlReport = false;
    var reportDir = opt('report_dir', null);
    var inferReportDir = reportDir === null;
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
        if (inferReportDir && reportDir === null) {
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
        'java', '-cp', classpath, 'org.prebake.service.tools.ext.JUnitRunner',
        typeof testListener === 'function' ? '' + testListener : '',
        reportDir || '',
        reportTypes]
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