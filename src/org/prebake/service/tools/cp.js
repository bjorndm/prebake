({
  help: {
    summary: 'Copies files to a directory tree.',
    detail: [
        'This version of the cp command copies by glob transform.',
        'E.g. to copy all html files under the doc/ directory to ',
        'the same location under the www directory do',
        '  tools.cp("doc/**.html", "www/**.html");'].join('\n'),
    contact: 'Mike Samuel <mikesamuel@gmail.com>'
  },
  fire: function fire(opts, inputs, product, action, exec) {
    // Infer outputs from inputs
    var outGlob = action.outputs[0];
    var inGlob = action.inputs[0];
    var xform = glob.xformer(action.inputs, action.outputs);
    for (var i = 0, n = inputs.length; i < n; ++i) {
      var input = inputs[i];
      var output = xform(input);
      // TODO: use a more efficient backdoor for builtins
      // that avoids process overhead.
      exec('cp', input, output);
    }
  },
  checker: function (action) {
    try {
      glob.xformer(action.inputs, action.outputs);
    } catch (ex) {
      console.warn(
          'cannot cp %s to %s: %s',
          action.inputs, action.outputs, ex.message);
    }
  }
})
