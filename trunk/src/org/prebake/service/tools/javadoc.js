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
  help: 'Builds HTML Java API documentation from java source files',
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
    var outDir = opt('d');
    if (typeof outDir !== 'string') {
      outDir = glob.rootOf(action.outputs);
      if (typeof outDir !== 'string') {
        throw new Error(
            'Could not infer documentation root from ' + action.inputs);
      }
    }
    var links = opt('link', []);
    var header = opt('header');
    var footer = opt('footer');
    var top = opt('top');
    var bottom = opt('bottom');
    var visibility = opt('visibility');
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
        // TODO: Strip directories if there is a com/org/net as a path element.
        extraClasspath.push(glob.prefix(input));
      } else if (endsWithJava.test(input)) {
        // TODO: Strip directories if there is a com/org/net as a path element.
        sourcePath.push(glob.prefix(input));
      }
    }
    if (extraClasspath.length) {
      classpath = classpath.split(pathSeparator).concat(extraClasspath)
          .join(pathSeparator);
    }
    var command = ['javadoc', '-d', outDir, '-classpath', classpath, '-quiet'];
    for (var i = 0; i < links.length; ++i) { command.push('-link', links[i]); }
    if (sourcePath.length) {
      command.push('-sourcepath', sourcePath.join(pathSeparator));
    }
    if (typeof header === 'string') { command.push('-header', header); }
    if (typeof footer === 'string') { command.push('-footer', footer); }
    if (typeof top === 'string') { command.push('-top', top); }
    if (typeof bottom === 'string') { command.push('-bottom', bottom); }
    switch (visibility) {
      case 'private': case 'protected': case 'package':
        command.push('-' + visibility);
        break;
      case undefined: break;
      default: throw new Error('Bad visibility ' + visibility);
    }
    command = command.concat(sources);
    return os.exec.apply({}, command).run();
  }
});
