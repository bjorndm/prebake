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
  help: 'Pack or unpack a java Archive.',
  check: function (action) {
    // TODO check d, cp options.
  },
  fire: function fire(inputs, product, action, os) {
    var opts = action.options;
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
      var xform = glob.xformer(
          jarsourcedir.replace(sys.io.file.separator, '/') + '/**', '**');
      jarcontent = Array.map(inputs, xform);
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
      // TODO: stop working with the jar commands's ridiculous manifest format.
      // Why do properties files not work for specifying a properties file?
      var manifestFileContent = manifestBuf.join('\n');
      for (var brokenLines;
           (brokenLines = manifestFileContent.replace(
                /(^|[\r\n])(.{40})(?=.)/,
                function (_, brk, line) {
                  var pre = line.replace(/\s+$/, '');
                  return brk + pre + '\n  ' + line.substring(pre.length);
                })) !== manifestFileContent;
           manifestFileContent = brokenLines) {
      }
      if (0 !== os.exec('echo', manifestFileContent)
          .writeTo(manifestFile).run().waitFor()) {
        return {
          waitFor: function () { return -1; },
          run: function () { return this; }
        };
      }
      command.push(manifestFile);
    }
    command.push(jarfile);
    if (jarsourcedir) {
      for (var i = 0, n = jarcontent.length; i < n; ++i) {
        command.push('-C', jarsourcedir, jarcontent[i]);
      }
    } else {
      command = command.concat(jarcontent);
    }
    return os.exec.apply({}, command);
  }
});
