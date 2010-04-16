package org.prebake.service.bake;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.fs.FileAndHash;
import org.prebake.fs.StubFileVersioner;
import org.prebake.js.YSON;
import org.prebake.os.OperatingSystem;
import org.prebake.os.StubProcess;
import org.prebake.service.plan.Action;
import org.prebake.service.plan.Product;
import org.prebake.service.tools.ToolProvider;
import org.prebake.service.tools.ToolSignature;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubScheduledExecutorService;

import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ValueFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@ParametersAreNonnullByDefault
public class BakerTest extends PbTestCase {
  private Tester tester;

  @Before
  public void init() {
    tester = new Tester();
  }

  @After
  public void tearDown() throws IOException {
    tester.close();
  }

  @Test
  public final void testBake() throws Exception {
    System.err.println("CP0");
    tester.withFileSystem(
        "/",
        "  tmp/",
        "  cwd/",
        "    foo/",
        "      a.txt  \"foo a text\"",
        "      a.html \"foo a html\"",
        "      b.txt  \"foo b text\"",
        "      b.html \"foo b html\"",
        "    bar/",
        "      a.txt  \"bar a text\"",
        "      a.html \"bar a html\"",
        "      b.txt  \"bar b text\"",
        "      b.html \"bar b html\"",
        "    toos/",
        ("      cp.js  \"({ \\n"
         + "  name:'cp', \\n"
         + "  fire: function (opts, inputs, action, product, exec) { \\n"
         // Sanity check all the inputs.
         + "    if (typeof opts !== 'object' \\n"
         // See options map below
         + "        || (JSON.stringify({foo:'bar'}) \\n"
         + "            !== JSON.stringify(opts))) { \\n"
         + "      throw new Error('wrong options'); \\n"
         + "    } \\n"
         + "    if (!(inputs instanceof Array)) { \\n"
         + "      throw new Error('' + inputs); \\n"
         + "    } \\n"
         + "    if (action.tool !== 'cp') { \\n"
         + "      throw new Error('' + tool); \\n"
         + "    } \\n"
         + "    if ('foo' !== product.name) { \\n"
         + "      throw new Error('product ' + JSON.stringify(product)); \\n"
         + "    } \\n"
         + "    if (typeof exec !== 'function') { \\n"
         + "      throw new Error('exec=' + exec); \\n"
         + "    } \\n"
         // Infer outputs from inputs
         + "    var outGlob = action.outputs[0]; \\n"
         + "    var inGlob = action.inputs[0]; \\n"
         + "    var inPrefix = inGlob.substring(0, inGlob.indexOf('*')); \\n"
         + "    var outPrefix = outGlob.substring(0, outGlob.indexOf('*')); \\n"
         + "    console.log('inPrefix =' + inPrefix); \\n"
         + "    console.log('outPrefix=' + outPrefix); \\n"
         + "    for (var i = 0, n = inputs.length; i < n; ++i) { \\n"
         + "      var input = inputs[i]; \\n"
         + "      console.log('input   =' + input); \\n"
         + "      var output = outPrefix + input.substring(inPrefix.length);\\n"
         + "      console.log('  output=' + output); \\n"
         + "      exec('cp', input, output); \\n"
         + "    } \\n"
         + "  } \\n"
         + "})\""))
        .withProduct(product(
            "foo",
            action(
                "cp",
                ImmutableMap.<String, Object>builder().put("foo", "bar")
                    .build(),
                "foo/**", "bar/**")))
        .withTool(tool("cp"), "/cwd/tools/cp.js")
        .expectSuccess(true)
        .build("foo");
  }

  // TODO: unrecognized product names
  // TODO: actions time out
  // TODO: product invalidated when tool changes
  // TODO: product invalidated when input changes
  // TODO: product invalidated when input created
  // TODO: product invalidated when input deleted
  // TODO: output globs that overlap inputs
  // TODO: process returns error code.
  // TODO: process takes a long time.
  // TODO: changed output is updated

  private final class BakerCallback implements Function<Boolean, Void> {
    final boolean golden;
    boolean called = false;

    BakerCallback(boolean golden) { this.golden = golden; }

    public synchronized Void apply(Boolean success) {
      tester.logger.log(Level.INFO, "Callback called with {0}", success);
      assertFalse(called);
      called = true;
      assertEquals(this.golden, success);
      return null;
    }
  }

  private class Tester {
    FileSystem fs;
    private OperatingSystem os;
    private StubFileVersioner files;
    private Logger logger;
    private ScheduledExecutorService execer;
    private StubToolProvider toolbox;
    private Baker baker;
    private boolean successExpectation;

    Tester withFileSystem(String... asciiArt) throws IOException {
      return withFileSystem(fileSystemFromAsciiArt(
          "/cwd", Joiner.on('\n').join(asciiArt)));
    }

    Tester withFileSystem(FileSystem fs) throws IOException {
      this.fs = fs;
      os = new StubOperatingSystem(fs);
      logger = getLogger(Level.INFO);
      files = new StubFileVersioner(fs.getPath(".").toAbsolutePath(), logger);
      execer = new StubScheduledExecutorService();
      toolbox = new StubToolProvider();
      baker = new Baker(os, files, logger, execer);
      baker.setToolBox(toolbox);
      return this;
    }

    Tester withTool(ToolSignature sig, String path) {
      toolbox.sigs.put(sig.name, sig);
      toolbox.toolPaths.put(sig.name, fs.getPath(path));
      return this;
    }

    Tester withProduct(Product p) {
      baker.prodListener.artifactChanged(p);
      return this;
    }

    Tester expectSuccess(boolean success) {
      this.successExpectation = success;
      return this;
    }

    Tester build(String productName) throws Exception {
      for (Future<ToolSignature> sigF : toolbox.getAvailableToolSignatures()) {
        baker.toolListener.artifactChanged(sigF.get());
      }
      BakerCallback callback = new BakerCallback(successExpectation);
      Boolean result = baker.build("foo", callback).get();
      synchronized (callback) {
        assertTrue(callback.called);
      }
      assertEquals(result, successExpectation);
      return this;
    }

    void close() throws IOException {
      if (toolbox != null) { toolbox.close(); }
      if (files != null) { files.close(); }
      if (execer != null) { execer.shutdown(); }
      if (fs != null) { fs.close(); }
    }
  }

  /**
   * A stub operating system that knows four commands:<table>
   *   <tr><td>cp<td>copy</tr>
   *   <tr><td>cat<td>concatenates inputs to the last argument</tr>
   *   <tr><td>munge<td>appends to each file the reverse of the previous</tr>
   *   <tr><td>bork<td>appends "Bork!" to the end of each argument</r>
   * </ul>
   */
  private static class StubOperatingSystem implements OperatingSystem {
    private final FileSystem fs;
    StubOperatingSystem(FileSystem fs) { this.fs = fs; }

    public Path getTempDir() {
      Path p = fs.getPath("/tmpdir");
      if (p.notExists()) {
        try {
          p.createDirectory();
        } catch (IOException ex) {
          throw new IOError(ex);
        }
      }
      return p;
    }

    public Process run(final Path cwd, String command, final String... argv)
        throws IOException {
      if (command.equals("cp")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws Exception {
                if (argv.length != 2) { return -1; }
                cwd.resolve(argv[0]).copyTo(fs.getPath(argv[1]));
                return 0;
              }
            });
      } else if (command.equals("cat")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                OutputStream out = cwd.resolve(argv[argv.length - 1])
                    .newOutputStream(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                  for (int inp = 0; inp < argv.length - 1; ++inp) {
                    InputStream in = cwd.resolve(argv[inp]).newInputStream();
                    try {
                      ByteStreams.copy(in, out);
                    } finally {
                      in.close();
                    }
                  }
                } finally {
                  out.close();
                }
                return 0;
              }
            });
      } else if (command.equals("munge")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                OutputStream out = cwd.resolve(argv[argv.length - 1])
                    .newOutputStream(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                  for (int inp = 0; inp < argv.length - 1; ++inp) {
                    InputStream in = cwd.resolve(argv[inp]).newInputStream();
                    try {
                      byte[] bytes = ByteStreams.toByteArray(in);
                      for (int n = bytes.length / 2, i = n / 2; --i >= 0;) {
                        byte b = bytes[i];
                        bytes[i] = bytes[n - i - 1];
                        bytes[n - i - 1] = b;
                      }
                      out.write(bytes);
                    } finally {
                      in.close();
                    }
                  }
                } finally {
                  out.close();
                }
                return 0;
              }
            });
      } else if (command.equals("bork")) {
        return new StubProcess(
            new Function<String, String>() {
              public String apply(String from) { return ""; }
            }, new Callable<Integer>() {
              public Integer call() throws IOException {
                for (String arg : argv) {
                  OutputStream out = cwd.resolve(arg).newOutputStream(
                      StandardOpenOption.CREATE,
                      StandardOpenOption.TRUNCATE_EXISTING);
                  Writer w = new OutputStreamWriter(out, Charsets.UTF_8);
                  try {
                    w.write("Bork!");
                  } finally {
                    w.close();
                  }
                }
                return 0;
              }
            });
      } else {
        throw new FileNotFoundException(command);
      }
    }
  }

  private Product product(String name, Action action) {
    return new Product(
        name, null, action.inputs, action.outputs,
        Collections.singletonList(action), false,
        tester.fs.getPath("plans/" + name + ".js"));
  }

  private static ToolSignature tool(String name) {
    return tool(name, null, null);
  }

  private static ToolSignature tool(String name, @Nullable Documentation docs) {
    return tool(name, null, docs);
  }

  private static ToolSignature tool(
      String name, @Nullable String checker, @Nullable Documentation docs) {
    return new ToolSignature(
        name, checker != null ? new YSON.Lambda(checker) : null, docs, true);
  }

  private static Action action(
      String tool, String input, String output) {
    return action(tool, ImmutableMap.<String, Object>of(), input, output);
  }

  private static Action action(
      String tool, ImmutableMap<String, ?> options,
      String input, String output) {
    return new Action(
        tool, Collections.singletonList(Glob.fromString(input)),
        Collections.singletonList(Glob.fromString(output)),
        options);
  }

  private static Action action(
      String tool, List<String> inputs, List<String> outputs) {
    List<Glob> inputGlobs = Lists.newArrayList();
    for (String input : inputs) { inputGlobs.add(Glob.fromString(input)); }
    List<Glob> outputGlobs = Lists.newArrayList();
    for (String output : outputs) { outputGlobs.add(Glob.fromString(output)); }
    return new Action(
        tool, inputGlobs, outputGlobs, ImmutableMap.<String, Object>of());
  }

  final class StubToolProvider implements ToolProvider {
    Map<String, ToolSignature> sigs = Maps.newHashMap();
    Map<String, Path> toolPaths = Maps.newHashMap();
    public List<Future<ToolSignature>> getAvailableToolSignatures() {
      List<Future<ToolSignature>> out = Lists.newArrayList();
      for (ToolSignature sig : sigs.values()) {
        ValueFuture<ToolSignature> f = ValueFuture.create();
        f.set(sig);
        out.add(f);
      }
      return out;
    }
    public FileAndHash getTool(String toolName) throws IOException {
      return tester.files.load(toolPaths.get(toolName));
    }
    public void close() throws IOException {}
  }
}
