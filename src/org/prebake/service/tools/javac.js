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
  var ok = true;
  var opts = action.options;
  var outDir, classpath, debug, nowarn = false;
  var hop = {}.hasOwnProperty;
  for (var k in opts) {
    if (!hop.call(opts, k)) { continue; }
    switch (k) {
      case 'cp': case 'classpath':
        classpath = opts[k];
        if (classpath instanceof Array) {
          classpath = classpath.slice();
        } else if (typeof classpath === 'string') {
          classpath = String(classpath).split(sys.io.path.separator);
        } else if (classpath !== undefined && classpath !== null) {
          console.error('Bad classpath ' + JSON.stringify(classpath));
          ok = false;
          classpath = undefined;
        }
        break;
      case 'd':
        outDir = opts[k];
        break;
      case 'g':
        debug = opts[k];
        switch (debug) {
          case true: debug = ''; break;
          case false: case 'none': debug = ':none'; break;
          case 'lines': case 'vars': case 'source':
            debug = ':' + debug;
            break;
          default:
            console.warn(
                'Unrecognized value for flag "g": ' + JSON.stringify(debug));
            if (typeof debug === 'string') {
              console.didYouMean(debug, 'none', 'lines', 'vars', 'source');
            }
            debug = undefined;
            break;
        }
        break;
      case 'nowarn':
        nowarn = opts[k];
        if (typeof nowarn !== 'boolean') {
          console.warn(
              'Expected boolean for option nowarn, not '
              + JSON.stringify(nowarn));
          nowarn = nowarn;
        }
        break;
      default:
        console.warn('Unrecognized option ' + k);
        console.didYouMean(k, 'cp', 'classpath', 'd', 'g', 'nowarn');
        break;
    }
  }
  if (typeof outDir !== 'string') {
    outDir = glob.rootOf(action.outputs);
  }
  if (!outDir) {
    console.error(
        'Cannot determine output directory for class files.'
        + '  Please include a tree root in your output globs.'
        + '  E.g., "lib///**.class"');
    ok = false;
  }
  if (opt_config) {
    opt_config.outDir = outDir;
    opt_config.classpath = classpath;
    opt_config.debug = debug;
    opt_config.nowarn = nowarn;
  }
  return ok;
}

({
  help: 'Java compiler.  // TODO: usage',
  check: decodeOptions,
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!decodeOptions(action, config)) {
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
    var command = ['javac', '-d', config.outDir, '-Xprefer:source'];
    if (classpathStr) { command.push('-cp', classpathStr); }
    if (typeof config.debug === 'string') {
      command.push('-g' + config.debug);
    }
    if (config.nowarn) { command.push('-nowarn'); }
    command = command.concat(sources);
    return os.exec.apply({}, command);
  }
});
