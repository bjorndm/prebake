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

function decodeOptions(action, opt_config) {
  if (!opt_config) { opt_config = {}; }
  var hop = {}.hasOwnProperty;
  var options = action.options;

  var effort, priority, relaxed = false, classpath;

  for (var k in options) {
    if (!hop.call(options, k)) { continue; }
    switch (k) {
      case 'effort':
        effort = String(options[k]);
        switch (effort) {
          case 'min': case 'default': case 'max': break;
          default:
            console.warn('Bad effort ' + effort);
            console.didYouMean(effort, 'min', 'default', 'max');
            effort = undefined;
            break;
        }
        break;
      case 'priority':
        priority = String(options[k]);
        switch (priority) {
          case 'low': case 'medium': case 'high': break;
          default:
            console.warn('Bad priority ' + priority);
            console.didYouMean(priority, 'low', 'medium', 'high');
            priority = undefined;
            break;
        }
        break;
      case 'relaxed':
        relaxed = options[k];
        if (typeof relaxed !== 'boolean') {
          console.warn(
              'Option relaxed was not boolean, was ' + JSON.stringify(relaxed));
          relaxed = false;
        }
        break;
      case 'cp': case 'classpath':
        classpath = options[k];
        break;
      default:
        console.warn('Unrecognized option ' + k);
        break;
    }
  }
  if (classpath instanceof Array) {
    classpath = classpath.slice(0);
  } else if (classpath) {
    classpath = String(classpath).split(sys.io.path.separator);
  } else {
    classpath = [];
  }
  opt_config.effort = effort;
  opt_config.priority = priority;
  opt_config.relaxed = relaxed;
  opt_config.classpath = classpath;
  return true;
}

({
  help: 'Runs FindBugs to find common problems in Java source code',
  check: decodeOptions,
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!decodeOptions(action, config)) {
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
