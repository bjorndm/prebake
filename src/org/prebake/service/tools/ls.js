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
  help:
    'Dumps a listing of its input files to the exact file matched by the input',
  checker: function (action) {
    // Require an exact file in the output glob.
    if (action.outputs.length) {
      try {
        glob.xformer('foo', action.outputs)('foo');
      } catch (ex) {
        switch (action.outputs.length) {
          case 1:
            console.error('Bad output %s.  Need full path', action.outputs[0]);
            break;
          default: console.error('Too many outputs'); break;
        }
      }
    }
  },
  fire: function fire(opts, inputs, product, action, os) {
    var outFile;
    // If no output is specified, can still be piped out.
    if (action.outputs.length) {
      try {
        outFile = glob.xformer('foo', action.outputs)('foo');
      } catch (ex) {
        switch (action.outputs.length) {
          case 1:
            console.error('Bad output %s.  Need full path', action.outputs[0]);
            break;
          default: console.error('Too many outputs'); break;
        }
        return { waitFor: function () { return -1; } };
      }
    }
    var p = os.exec.apply({}, ['ls'].concat(inputs));
    if (outFile !== undefined) { p = p.writeTo(outFile); }
    return p.run();
  }
})
