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
    for (var k in action.options) {
      console.warn('Unrecognized option ' + k);
    }
    var outFile = undefined;
    // If no output is specified, can still be piped out.
    if (action.outputs.length) {
      try {
        // Require an exact file in the output glob.
        outFile = glob.xformer('foo', action.outputs)('foo');
      } catch (ex) {
        switch (action.outputs.length) {
          case 1:
            console.error('Bad output %s.  Need full path', action.outputs[0]);
            break;
          default: console.error('Too many outputs'); break;
        }
        return false;
      }
    }
    if (opt_config) { opt_config.outFile = outFile; }
    return true;
  }

  return ({
    help: ('Dumps a listing of its input files to stdout or to the ' +
           'fully qualified output file if one is specified.'),
    check: prelim,
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!prelim(action, config)) { return os.failed; }
      var outFile = config.outFile;
      var p;
      if (inputs.length === 0) {
        p = os.exec('echo');  // ls behaves differently with 0 inputs.
      } else {
        p = os.exec(['ls'].concat(inputs));
      }
      if (outFile !== undefined) { p = p.writeTo(outFile); }
      return p;
    }
  });
})()
