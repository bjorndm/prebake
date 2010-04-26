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
  checker: function (action) {
    // TODO check options.
  },
  fire: function fire(opts, inputs, product, action, exec) {
    function opt(name, opt_defaultValue) {
      if ({}.hasOwnProperty.call(opts, name)) {
        return opts[name];
      } else {
        return opt_defaultValue;
      }
    }
    var testListener = opt('listener');
    var pathSeparator = sys.io.path.separator;
    var classpath = opt('cp') || opt('classpath');
    if (classpath instanceof Array) {
      classpath = classpath.join(pathSeparator);
    } else if (classpath) {
      classpath = String(classpath);
    } else {
      classpath = '';
    }
    var extraClasspath = [];
    var testClasses = [];
    var extRe = /.[^\/\\.]$/;
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var ext = extRe.match(input);
      if (ext) {
        switch (ext[0]) {
          case '.jar': extraClasspath.push(input); break;
          case '.class': testClasses.push(input); break;
        }
      }
    }
    if (extraClasspath.length) {
      classpath = Array.filter(
          classpath.split(pathSeparator).concat(java_classpath.split(pathSeparator)),
          function (x) { return !!x; })
          .concat(extraClasspath)
          .join(pathSeparator);
    }
    var wantsHtmlReport = false,
        wantsJsonReport = false,
        wantsXmlReport = false;
    var reportDir = opt('reportDir', null);
    var inferReportDir = reportDir === null;
    for (var i = 0, n = action.outputs.length; i < n; ++i) {
      var output = action.outputs[i];
      var ext = extRe.match(output);
      if (ext) {
        switch (ext[0]) {
          case '.html': wantsHtmlReport = true; break;
          case '.json': wantsJsonReport = true; break;
          case '.xml':  wantsXmlReport = true; break;
          default: continue;
        }
        if (inferReportDir) {
          // The report directory is the one that contains the 
          var m = /^.+?(?=\/[^\/]$|\*{1,2}(?:\/|[^*]+\*))/.match(output);
          if (m) {
            var prefix = m[0];
            if (reportDir === null
                || (prefix.length < reportDir
                    && prefix === reportDir.substring(0, prefix.length))) {
              reportDir = prefix;
            }
          }
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
    exec.apply({}, command);
  }
});
