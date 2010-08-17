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
  var schemaModule = load('/--baked-in--/tools/json-schema.js')({ load: load });

  // TODO: turn into a regex for code and header separately.
  const SOURCE_EXT = /\.(?:c(?:c|pp?|xx|\+\+)?|C|mm?|M|F|fpp|FPP|r|s)$/;
  const PREPROCESSED_EXT
      = /\.(?:ii?|mii?|f|for|FOR|F|f(?:77|90|95)|ads|S|ratfor|java)$/;
  const ASM_EXT = /\.[sS]$/;
  const HEADER_EXT = /\.(?:hh?)$/;
  const OBJ_EXT = /\.(\.o|obj)$/;
  // Otherwise an object file.

  function def(type, defaultValue) {
    if (defaultValue === undefined) {
      return { type: 'optional', delegate: type };
    }
    return {
      type: 'default',
      delegate: type,
      defaultValue: (function (x) { return x; }).bind(defaultValue)
    };
  }

  function anyOf(items) {
    return { type: 'Array', delegate: items };
  }

  var optBool = def('boolean');
  var defTrue = def('boolean', true);

  var WARNINGS = [
    'abi',
    'ctor-dtor=privacy',
    'non-virtual-dtor',
    'reorder',
    'effcpp',
    'deprecated',
    'strict-null-sentinel',
    'non-template-friend',
    'old-style-cast',
    'overloaded-virtual',
    'pmf-conversions',
    'sign-promo',
    'import',
    '#warnings',
    'char-subscripts',
    'comment',
    'fatal-errors',
    'format',
    'format-y2k',
    'format-extra-args',
    'format-zero-length',
    'format-nonliteral',
    'format-security',
    'format=2',
    'nonnull',
    'init-self',
    'implicit-int',
    'implicit-function-declaration',
    'error-implicit-function-declaration',
    'implicit',
    'main',
    'missing-braces',
    'missing-include-dirs',
    'parentheses',
    'sequence-point',
    'return-type',
    'switch',
    'switch-default',
    'switch-enum',
    'trigraphs',
    'unused-function',
    'unused-label',
    'unused-parameter',
    'unused-variable',
    'unused-value',
    'unused',
    'uninitialized',
    'unknown-pragmas',
    'strict-aliasing',
    'strict-aliasing=2',
    'div-by-zero',
    'system-headers',
    'float-equal',
    'four-char-constants',
    'traditional',
    'declaration-after-statement',
    'discard-qual',
    'undef',
    'endif-labels',
    'shadow',
    'pointer-arith',
    'bad-function-cast',
    'cast-qual',
    'cast-align',
    'write-strings',
    'conversion',
    'shorten-64-to-32',
    'sign-compare',
    'aggregate-return',
    'strict-prototypes',
    'old-style-definition',
    'missing-prototypes',
    'missing-declarations',
    'missing-field-initializers',
    'missing-noreturn',
    'missing-format-attribute',
    'multichar',
    'deprecated-declarations',
    'packed',
    'padded',
    'redundant-decls',
    'nested-externs',
    'unreachable-code',
    'inline',
    'invalid-offsetof',
    'int-to-pointer-cast',
    'pointer-to-int-cast',
    'invalid-pch',
    'long-long',
    'variadic-macros',
    'disabled-optimization',
    'pointer-sign',
    'stack-protector'
    ];

  var F_DUMP_OPTIONS = def({
        type: 'union',
        options: [
          'boolean',
          ['all'],
          anyOf([
              'address',
              'slim',
              'raw',
              'details',
              'stats',
              'blocks',
              'vops',
              'lineno',
              'uid'
            ])
        ]
      });

  var options = {
    type: 'Object',
    properties: {
      arch: def('string'),
      passExitCodes: def('boolean', false),
      stage: def([
          'c',  // stop after link
          'S',  // stop ater compile
          'E',  // stop after preprocess
          '*'   // do all
          ]),
      o: def('string'),  // output file
      v: optBool,  // verbose commands
      pipe: optBool,  // inter-stage pipes
      cpp: optBool,  // include C++ libraries and treat C source as C++
      std: def(['ansi', 'c89', 'iso9899:199409', 'c9x', 'gnu89', 'gnu9x',
                'c++98', 'gnu++98']),
      pedantic: def([true, false, 'errors']),
      fChar: def(['signed', 'unsigned'], 'unsigned'),
      fBitfields: def(['signed', 'unsigned'], 'signed'),
      auxInfo: def('string'),  // TODO: infer from output extension?
      fAsm: optBool,  // disable inline assembly
      fBuiltin: def(
        { type: 'union',
          options: [ 'boolean', { type: 'Array', delegate: 'string' }] }
      ),
      fFreestanding: optBool,
      fMsExtensions: optBool,
      fCondMismatch: optBool,
      fNonLvalueAssign: optBool,
      trigraphs: optBool,
      fabiVersion: def(['1', '2', '3']),
      fAccessControl: defTrue,
      fConserveSpace: optBool,
      fElideConstructors: defTrue,
      fEnforceEhSpecs: defTrue,
      fForScope: defTrue,
      fImplicitTemplates: defTrue,
      fImplicitInlineTemplates: defTrue,
      fImplementInlines: defTrue,
      fMsExtensions: optBool,
      fPermissive: optBool,
      fRepo: optBool,
      fRtti: defTrue,
      fStats: optBool,
      fTemplateDepth: def('uint32'),
      fThreadsafeStatics: defTrue,
      fUseCxaAtexit: optBool,
      fVisibilityInlinesHidden: optBool,
      fVisibilityMsCompat: optBool,
      fWeak: defTrue,
      fDefaultInline: defTrue,
      fMessageLength: def('uint32'),
      fDiagnosticShowLocation: def(['once', 'every-line'], 'once'),
      fSyntaxOnly: optBool,
      W: def(anyOf(['all', 'extra', 'most', 'error'].concat(WARNINGS))),
      w: def(['none', anyOf(WARNINGS)]),
      WlargerThan: def('uint32'),
      Wnormalized: def(['none', 'id', 'nfc', 'nfkc']),
      g: def({
        type: 'union',
        options: [
          'boolean',
          'uint32',
          ['gdb', 'stabs', 'stabs+', 'dwarf-2'],
          {
            type: 'Object',
            properties: {
              level: 'uint32',
              format: ['gdb', 'stabs']
            }
          }
        ]
      }),
      fLimitDebugInfo: optBool,
      fEliminateUnusedDebugSymbols: optBool,
      fEliminateDwarf2Dups: optBool,
      p: optBool,
      pg: optBool,
      Q: optBool,
      fTimeReport: optBool,
      fMemReport: optBool,
      fOptDiary: optBool,
      fProfileArcs: optBool,
      fTreeBasedProfiling: optBool,
      fTestCoverage: optBool,
      d: anyOf([
          'A', 'B', 'c', 'C', 'd', 'D', 'E', 'f', 'g', 'G', 'h', 'i', 'j', 'k',
          'l', 'L', 'm', 'M', 'n', 'N', 'o', 'r', 'R', 's', 'S', 't', 'T', 'V',
          'w', 'z', 'Z', 'a', 'H', 'm', 'p', 'P', 'v', 'x', 'y']),
      dDumpNoaddr: optBool,
      fDumpUnnumbered: optBool,
      fDumpTranslationUnit: F_DUMP_OPTIONS,
      fDumpClassHierarchy: F_DUMP_OPTIONS,
      fDumpIpo: def(['all', 'cgraph']),
      fDumpTree: def({
        type: 'Object',
        properties: {
          options: F_DUMP_OPTIONS,
          switches: [
            'all', 
            def(anyOf([
                'original', 'optimized', 'inlined', 'gimple', 'cfg', 'vcg',
                'ch', 'ssa', 'salias', 'alias', 'ccp', 'storeccp', 'pre', 'fre',
                'copyprop', 'store_copyprop', 'dce', 'mudflap', 'sra', 'sink',
                'dom', 'dse', 'phiopt', 'formprop', 'copyrename', 'nrv', 'vect',
                'vrp']))
          ]
        }
      }),
      fTreeVectorizerVerbose: def('uint32'),
      fRandomSeed: def('string'),
      fSchedVerbose: def('uint32'),
      saveTemps: optBool,   // TODO: assume if there are .i and .s in the output globs
      time: optBool,
      // TODO: fvar-tracking
    }
  };

  function decodeOptions(optionsSchema, action, opt_config) {
    // For this to be a mobile function we can't use schemaModule defined above.
    var schemaModule = load('/--baked-in--/tools/json-schema.js')(
        { load: load });
    var schemaOut = {};
    if (schemaModule.schema(optionsSchema).check(
            '_', action.options || {}, schemaOut, console,
            // Shows up in the error stack.
            [action.tool + '.action.options'])) {
      if (!schemaOut._.stage) {
        // Infer the output stage from the output globs.
        const ALL = 0;
        const COMPILE = 2;
        const PREPROCESS = 1;
        var stage = ALL;  // The default
        for (var i = 0, n = action.outputs.length; i < n; ++i) {
          var output = action.outputs[i];
          if (PREPROCESSED_EXT.test(output)) {
            if (stage < PREPROCESS) { stage = PREPROCESS; }
          } else if (OBJ_EXT.test(output)) {
            if (stage < COMPILE) { stage = COMPILE; }
          // TODO: asm instruction for link?
          } else {
            stage = ALL;
            break;
          }
        }
        switch (stage) {
          case ALL: schema._.stage = '*'; break;
          case COMPILE: schema._.stage = 'c'; break;
          case PREPROCESS: schema._.stage = 'E'; break;
        }
      }
      if (!schemaOut._.o) {
        if (action.outputs.length === 1) {
          try {
            schemaOut._.o = glob.xformer('', action.outputs[0])('');
          } catch (e) {
            // OK.  Let gcc figure it out.
          }
        }
      }
      if (!schemaOut._.fDumpTranslationUnit === undefined) {
        schemaOut._.fDumpTranslationUnit = '.tu' in outputExts;
      }
      if (opt_config) { schemaModule.mixin(schemaOut._, opt_config); }
      return true;
    } else {
      return false;
    }
  }

  function flatten(var_args) {
    var out = [];
    var k = -1;
    function flattenOntoOut(args) {
      for (var i = 0, n = args.length; i < n; ++i) {
        var item = args[i];
        if (item instanceof Array) {
          flattenOntoOut(item);
        } else {
          out[++k] = item;
        }
      }
    }
    flattenOntoOut(arguments);
    return out;
  }

  function toGccArch(javaArch) {
    // TODO: flesh this out to support AMD and x86 64b.
    if (/power/i.test(javaArch)) {
      if (/64/.test(javaArch)) {
        return 'ppc64';
      } else {
        return 'ppc';
      }
    } else if (/86/.test(javaArch)) {
      return 'i386';
    } else {
      throw new Error('Unrecognized architecture ' + javaArch);
    }
  }

  return ({
    help: 'Reduces JavaScript source code size.'
        + '\n<pre class=\"prettyprint lang-js\">'
        + schemaModule.example(schemaModule.schema(options)) + '</pre>',
    check: decodeOptions.bind({}, options),
    fire: function fire(inputs, product, action, os) {
      var config = {};
      if (!decodeOptions(options, action, config)) { return os.failed; }
      var arch;
      if (config.arch) {
        arch = config.arch;
      } else if (product.bindings.arch) {
        try {
          arch = toGccArch(product.bindings.arch);
        } catch (e) {
          console.warning(
              'Did not recognize architecture ' + product.bindings.arch);
          // best effort
        }
      } else {
        arch = toGccArch(sys.os.arch);
      }

 // TODO: review everything below
      throw new Error("TODO: finish implementing gcc tool");
      var outDir = glob.rootOf(action.outputs);
      if (outDir === null) {
        console.error(
            'Cannot determine output directory from '
            + JSON.stringify(action.outputs));
        return os.failed;
      }
      var minGroups = {};
      var xform = glob.xformer(action.inputs, action.outputs);
      var errs = false;
      var hop = minGroups.hasOwnProperty;
      // Generate a mapping from inputs to outputs.  If one or more of the inputs
      // map to the same output, then minimize them together.
      // E.g. for
      //    tools.jsmin('src/foo/*.js', 'out/foo/*-min.js')
      // one file would be generated per input, but for
      //    tools.jsmin('src/foo/*.js', 'out/foo/bundle.js')
      // one file is generated which contains the concatenation of the minified
      // version of all the inputs.
      for (var i = 0, n = inputs.length; i < n; ++i) {
        var input = inputs[i];
        var output = xform(input);
        if (output == null) {
          console.error('Cannot determine output for input %s', input);
          errs = true;
        }
        if (!hop.call(minGroups, output)) { minGroups[output] = []; }
        minGroups[output].push(input);
      }
      if (errs) { return os.failed; }
      var flags = ['jsmin'];
      if (!config.rename) { flags.push('--norename'); }
      flags.push('--');
      var procs = [];
      for (var output in minGroups) {
        if (!hop.call(minGroups, output)) { continue; }
        procs.push(os.exec(flags.concat(minGroups[output])).writeTo(output));
      }
      switch (procs.length) {
        case 0: return os.passed;
        case 1: return procs[0];
        default:
          return {
            run: function () {
              for (var i = 0, n = procs.length; i < n; ++i) { procs[i].run(); }
              return this;
            },
            waitFor: function () {
              var result = 0;
              for (var i = 0, n = procs.length; i < n; ++i) {
                result |= procs[i].waitFor();
              }
              return result;
            }
          };
      }
    }
  });
})()
