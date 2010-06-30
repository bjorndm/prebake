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

  return ({
    help: {
      summary: 'Copies files to a directory tree.',
      detail: (
          'This version of the cp command copies by glob transform.\n'
          + 'E.g. to copy all html files under the doc/ directory to'
          + ' the same location under the www directory do\n'
          + '<code class="prettyprint lang-js">'
          + '  tools.cp("doc/**.html", "www/**.html");</code>'),
      contact: 'Mike Samuel <mikesamuel@gmail.com>'
    },
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!prelim(action, config)) { return os.failed; }
      var xform = config.xform;
      var cmd = ['$$cp'];
      if (inputs.length) {
        // Use an InVmProcess to efficiently move many files.
        for (var i = 0, n = inputs.length; i < n; ++i) {
          var input = inputs[i];
          var output = xform(input);
          cmd.push(input, output);
          os.mkdirs(os.dirname(output));
        }
        return os.exec(cmd);
      } else {
        return os.passed;
      }
    },
    check: prelim
  });

})()
