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
  help: 'Pack or unpack a java Archive',
  checker: function (action) {
    // TODO check d, cp options.
  },
  fire: function fire(opts, inputs, product, action, os) {
    function opt(name, opt_defaultValue) {
      if ({}.hasOwnProperty.call(opts, name)) {
        return opts[name];
      } else {
        return opt_defaultValue;
      }
    }
    var operation = opt('operation');
    var jarfile;
    var jarcontent;
    var jarsourcedir = opt('C');
    var manifest = opt('manifest');
    if ((operation === 'x' || !operation)
        && inputs.length === 1 && /\.(jar|zip)$/.test(inputs[0].length)) {
      operation = 'x';
      jarfile = inputs[0];
      if (jarsourcedir === undefined) {
        jarsourcedir = glob.rootOf(action.outputs);
      }
      jarcontent = [];
    } else if (action.outputs.length === 1) {
      switch (operation) {
        case 'c': case 't': case undefined: break;
        default: throw new Error('Invalid operation ' + operation);
      }
      if (jarsourcedir === undefined) {
        jarsourcedir = glob.rootOf(action.inputs);
      }
      jarfile = glob.xformer('foo', action.outputs[0])('foo');
      jarcontent = '';
      var xform = glob.xformer(
          jarsourcedir.replace(sys.io.file.separator, '/') + '/**', '**');
      for (var i = 0, n = inputs.length; i < n; ++i) {
        jarcontent[i] = xform(inputs[i]);
      }
    } else {
      throw new Error('Cannot determine whether to jar orunjar');
    }

    var manifestBuf = [];
    if (manifest) {
      var hop = {}.hasOwnProperty;
      for (var k in manifest) {
        if (!hop.call(manifest, k)) { continue; }
        manifestBuf.push(k + ': ' + manifest[k]);
      }
    }
    var noManifest = manifestBuf.length === 0;
    var letterFlags = (operation || 'c')
        + (noManifest ? manifest === null ? 'M' : '' : 'm') + 'f';
    var command = ['jar', letterFlags];
    if (!noManifest) {
      var manifestFile = os.tmpfile('mf');
      if (0 !== os.exec('echo', manifestBuf.join('\n'))
          .writeTo(manifestFile).run().waitFor()) {
        return false;
      }
      command.push(manifestFile);
    }
    command.push(jarfile);
    if (jarsourcedir) { command.push('-C', jarsourcedir); }
    command = command.concat(jarcontent);
    return os.exec.apply({}, command).run();
  }
});
