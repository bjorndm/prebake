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
  help: 'Runs FindBugs to find common problems in Java source code',
  checker: function (action) {
    // TODO
  },
  fire: function fire(opts, inputs, product, action, os) {
    function opt(name, opt_defaultValue) {
      if ({}.hasOwnProperty.call(opts, name)) {
        return opts[name];
      } else {
        return opt_defaultValue;
      }
    }
    // TODO: heap size, java home, findbugs home, include/exclude, pluginList,
    // experimental
    var effort = opt('effort');
    var priority = opt('priority');
    var relaxed = opt('relaxed');
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
        outputFile = glob.xformer('foo', output)('foo');
        break;
      }
    }
    if (extraClasspath.length) {
      classpath = classpath.split(pathSeparator).concat(extraClasspath)
          .join(pathSeparator);
    }
    var command = ['findbugs', '-textui', '-progress'];
    if (effort) { command.push('-effort:' + effort); }
    switch (priority) {
      case 'low': case 'medium': case 'high': command.push('-' + priority); break;
      case undefined: break;
      default: throw new Error('bad priority ' + priority);
    }
    if (relaxed) { command.push('-relaxed'); }
    if (outputTypeFlag) { command.push('-' + outputTypeFlag); }
    if (outputFile) { command.push('-output', outputFile); }
    if (classpath) { command.push('-auxclasspath', classpath); }
    command = command.concat(sources);
    return os.exec.apply({}, command).run().waitFor() === 0;
  }
});
