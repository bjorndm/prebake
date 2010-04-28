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
  help: 'Java compiler.  // TODO: usage',
  checker: function (action) {
    // TODO check d, cp options.
  },
  fire: function fire(opts, inputs, product, action, exec) {
    function opt(name, opt_defaultValue) {
      if ({}.hasOwnProperty.call(opts, name)) {
        return opts[name];
      } else {
        return opt_defaultValue;
      }
    }
    // Infer outputs from inputs
    var outGlob = action.outputs[0];
    var outDir = opt('d');
    if (typeof outDir !== 'string') {
      outDir = outGlob.match(/^.*?(?=\/com\/|\/org\/|\/net\/|\/\*)/)[0];
    }
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
        case 'jar': extraClasspath.push(input); break;
        case 'class': break;
        default: sources.push(input); break;  // java, jsp, etc.
      }
    }
    var endsWithClass = /.class$/;
    for (var i = 0, n = action.inputs.length; i < n; ++i) {
      var input = action.inputs[i];
      if (endsWithClass.test(input)) {  // E.g. lib/**.class
        // TODO: Strip off directories if there is a com/org/net as a path element.
        extraClasspath.push(glob.prefix(input));
      }
    }
    if (extraClasspath.length) {
      classpath = classpath.split(pathSeparator).concat(extraClasspath)
          .join(pathSeparator);
    }
    var command = ['javac', '-d', outDir, '-cp', classpath].concat(sources);
    exec.apply({}, command);
  }
});
