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

function prelim(action, opt_config) {
  // xform is used to infer outputs from inputs
  var xform = undefined;
  for (var k in action.options) {
    console.warn('Unrecognized option ' + k);
  }
  try {
    xform = glob.xformer(action.inputs, action.outputs);
  } catch (ex) {
    console.error('Cannot map inputs to outputs : ' + ex.message);
    return false;
  }
  if (opt_config) { opt_config.xform = xform; }
  return true;
}

({
  help: {
    summary: 'Copies files to a directory tree.',
    detail: [
        'This version of the cp command copies by glob transform.',
        'E.g. to copy all html files under the doc/ directory to ',
        'the same location under the www directory do',
        '<code class="prettypring lang-js">',
        '  tools.cp("doc/**.html", "www/**.html");</code>'].join('\n'),
    contact: 'Mike Samuel <mikesamuel@gmail.com>'
  },
  fire: function fire(inputs, product, action, os) {
    var config = {};
    if (!prelim(action, config)) {
      return {
        run: function () { return this; },
        waitFor: function () { return -1; }
      };
    }
    var xform = config.xform;
    var processes = [];
    // Infer outputs from inputs.
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var output = xform(input);
      // TODO: use a more efficient backdoor for builtins
      // that avoids process overhead.
      os.mkdirs(os.dirname(output));
      processes.push(os.exec('cp', input, output).run());
    }
    return {
      run: function () {
        for (var i = 0, n = processes.length; i < n; ++i) {
          processes[i].run();
        }
        return this;
      },
      waitFor: function () {
        for (var i = 0, n = processes.length; i < n; ++i) {
          if (processes[i].waitFor()) {
            throw new Error('Could not copy ' + inputs[i]);
          }
        }
        console.log('Copied ' + n + (n !== 1 ? ' files' : ' file'));
        return 0;
      }
    };
  },
  check: prelim
})
