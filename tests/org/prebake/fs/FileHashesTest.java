package org.prebake.fs;

import org.prebake.core.ArtifactListener;
import org.prebake.core.Glob;
import org.prebake.core.Hash;
import org.prebake.util.PbTestCase;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileHashesTest extends PbTestCase {
  private FileSystem fs;
  private Environment env;
  private File tempDir;
  private FileHashes fh;

  @Before
  public void setUp() throws IOException {
    fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/cwd"));
    mkdirs(fs.getPath("/cwd/root"));
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    tempDir = Files.createTempDir();
    System.err.println("tempDir=" + tempDir + ", " + tempDir.exists());
    env = new Environment(tempDir, envConfig);
    fh = new FileHashes(env, fs.getPath("/cwd/root"), getLogger(Level.INFO));
  }

  @After
  public void cleanup() throws IOException {
    fh.close();
    fh = null;
    env.close();
    env = null;
    fs.close();
    fs = null;
    rmDirTree(tempDir);
    tempDir = null;
  }

  @Test public final void testUpdates() throws Exception {
    writeFile(fs.getPath("/cwd/root/a.cc"), "printf(\"Hello, World!\\n\");");
    writeFile(fs.getPath("/cwd/root/b.h"), "#include <zoicks>");
    writeFile(
        fs.getPath("/cwd/root/c.cpp"),
        "cout << \"Hello, \" << \"World!\" << eol;");
    fh.update(paths("root/a.cc", "root/b.h"));
    assertHash(
        "810dc8ffe666362187cd8f99da072d6e", paths("root/b.h", "root/c.cpp"));
    fh.update(paths("root/b.h"));
    assertHash(
        "810dc8ffe666362187cd8f99da072d6e", paths("root/b.h", "root/c.cpp"));
    writeFile(fs.getPath("root/b.h"), "#define ZOICKS");
    fh.update(paths("/cwd/root/b.h"));
    assertHash(
        "2e0413078a434e704f78b3d6b872bdc2", paths("root/b.h", "root/c.cpp"));
    writeFile(fs.getPath("root/b.h"), "#include <zoicks>");
    fh.update(paths("root/b.h"));
    assertHash(
        "810dc8ffe666362187cd8f99da072d6e", paths("root/b.h", "root/c.cpp"));
  }

  @Test public final void testFilesOutsideRoot() throws Exception {
    Path ra = fs.getPath("/cwd/root/a.cc");
    Path rb = fs.getPath("/cwd/root/b.cpp");
    Path b = fs.getPath("/cwd/b.cpp");
    Path b2 = fs.getPath("/cwd/b2.cpp");
    Path b3 = fs.getPath("/cwd/b3.cpp");
    writeFile(ra, "printf(\"Hello, World!\\n\");");
    writeFile(b, "cout << \"Hello, \" << \"World!\" << eol;");
    fh.update(Arrays.asList(ra, b));
    String hash = getHashStr(ra, b);
    writeFile(b, "cout << \"Howdy, \" << \"Texstar!\" << eol;");
    // Updates to files outside the root are ignored
    fh.update(paths(ra, b));
    assertEquals(hash, getHashStr(ra, b));
    // As is the initial content
    writeFile(b2, "cout << \"Howdy, \" << \"Texstar!\" << eol;");
    assertEquals(hash, getHashStr(ra, b2));
    // It's as if the file wasn't there.
    assertEquals(hash, getHashStr(ra, b3));
    // But if the second file does exits, the hash does change
    writeFile(rb, "cout << \"Hello, \" << \"World!\" << eol;");
    fh.update(paths(ra, rb));
    assertFalse(hash.equals(getHashStr(ra, rb)));
  }

  @Test public final void testInvalidation() throws Exception {
    class Tool implements NonFileArtifact {
      public final String name;
      public boolean valid;
      Tool(String name) { this.name = name; }
      public void markValid(boolean valid) { this.valid = valid; }
    }

    class Product implements NonFileArtifact {
      public final String name;
      public boolean valid;
      Product(String name) { this.name = name; }
      public void markValid(boolean valid) { this.valid = valid; }
    }

    final Map<String, Tool> tools = Maps.newHashMap();
    ArtifactAddresser<Tool> toolAddresser = new ArtifactAddresser<Tool>() {
      public Tool lookup(String address) { return tools.get(address); }
      public String addressFor(Tool t) { return t.name; }
    };
    Tool javat = new Tool("java");
    Tool gcct = new Tool("gcc");
    Tool javadoct = new Tool("javadoc");
    tools.put("java", javat);
    tools.put("gcc", gcct);
    tools.put("javadoc", javadoct);

    final Map<String, Product> products = Maps.newHashMap();
    ArtifactAddresser<Product> productAddresser
        = new ArtifactAddresser<Product>() {
      public Product lookup(String address) { return products.get(address); }
      public String addressFor(Product p) { return p.name; }
    };
    Product docsp = new Product("docs");
    Product javap = new Product("java");
    products.put("docs", docsp);
    products.put("java", javap);

    Path a = fs.getPath("/cwd/root/a");
    Path b = fs.getPath("/cwd/root/b");
    Path c = fs.getPath("/cwd/root/c");
    Path d = fs.getPath("/cwd/root/d");
    writeFile(a, "file a version 0");
    writeFile(b, "file b version 0");
    writeFile(c, "file c version 0");
    writeFile(d, "file d version 0");
    fh.update(paths(a, b, c, d));
    assertEquals(getHash(a), getHash(a));
    assertFalse(getHashStr(a).equals(getHashStr(b)));
    assertFalse(getHashStr(a).equals(getHashStr(c)));
    assertFalse(getHashStr(a).equals(getHashStr(d)));
    assertFalse(getHashStr(b).equals(getHashStr(c)));
    assertFalse(getHashStr(b).equals(getHashStr(d)));
    assertFalse(getHashStr(c).equals(getHashStr(d)));

    //      java    gcc   javadoc            Tools
    //       |       |      |  \
    //       |       |      |    \
    //       a       b      c      d         Files
    //       |     /   \    |    /
    //       |   /       \  |  /
    //       java         docs               Products

    assertTrue(fh.update(toolAddresser, javat, paths(a), getHash(a)));
    assertTrue(javat.valid);
    assertFalse(gcct.valid);
    assertFalse(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    // Mismatch between hash and deps
    assertFalse(fh.update(toolAddresser, gcct, paths(a, b), getHash(b)));
    assertTrue(javat.valid);
    assertFalse(gcct.valid);
    assertFalse(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    // Second attempt succeeds.
    assertTrue(fh.update(toolAddresser, gcct, paths(b), getHash(b)));
    assertTrue(javat.valid);
    assertTrue(gcct.valid);
    assertFalse(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    assertTrue(fh.update(toolAddresser, javadoct, paths(c, d), getHash(c, d)));
    assertTrue(javat.valid);
    assertTrue(gcct.valid);
    assertTrue(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    assertTrue(fh.update(productAddresser, javap, paths(a, b), getHash(a, b)));
    assertTrue(javat.valid);
    assertTrue(gcct.valid);
    assertTrue(javadoct.valid);
    assertTrue(javap.valid);
    assertFalse(docsp.valid);

    assertTrue(fh.update(
        productAddresser, docsp, paths(b, c, d), getHash(b, c, d)));
    assertTrue(javat.valid);
    assertTrue(gcct.valid);
    assertTrue(javadoct.valid);
    assertTrue(javap.valid);
    assertTrue(docsp.valid);

    Hash b0 = getHash(b);
    writeFile(b, "file b version 1");
    fh.update(paths(b, c));

    Hash b1 = getHash(b);
    assertFalse(b0.equals(b1));

    assertTrue(javat.valid);
    assertFalse(gcct.valid);
    assertTrue(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    Hash hash = getHash(b, c, d);
    writeFile(d, "file d version 1");
    fh.update(paths(d));  // Update file in between start of validation and end
    assertTrue(javat.valid);
    assertFalse(gcct.valid);
    assertFalse(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);

    // hash is not stale.
    assertFalse(fh.update(productAddresser, docsp, paths(b, c, d), hash));
    assertTrue(javat.valid);
    assertFalse(gcct.valid);
    assertFalse(javadoct.valid);
    assertFalse(javap.valid);
    assertFalse(docsp.valid);
  }

  @Test public final void testWatcher() throws IOException {
    GlobUnion fooTxt = new GlobUnion(
        "foo.txt", Arrays.asList(Glob.fromString("foo/**.txt")));
    GlobUnion barTxt = new GlobUnion(
        "bar.txt", Arrays.asList(Glob.fromString("bar/**.txt")));
    GlobUnion fooHtml = new GlobUnion(
        "foo.html", Arrays.asList(Glob.fromString("foo/**.html")));
    GlobUnion barHtml = new GlobUnion(
        "bar.html", Arrays.asList(Glob.fromString("bar/**.html")));
    GlobUnion foo = new GlobUnion(
        "foo", Arrays.asList(Glob.fromString("foo/**")));
    GlobUnion bar = new GlobUnion(
        "bar", Arrays.asList(Glob.fromString("bar/**")));
    GlobUnion txt = new GlobUnion(
        ".txt", Arrays.asList(Glob.fromString("**.txt")));
    GlobUnion html = new GlobUnion(
        ".html", Arrays.asList(Glob.fromString("**.html")));

    final Logger logger = getLogger(Level.INFO);
    ArtifactListener<GlobUnion> listener = new ArtifactListener<GlobUnion>() {
      public void artifactChanged(GlobUnion artifact) {
        logger.log(Level.INFO, "changed " + artifact.name);
      }
      public void artifactDestroyed(String artifactName) {
        logger.log(Level.INFO, "destroyed " + artifactName);
      }
    };

    fh.watch(fooTxt, listener);
    fh.watch(barTxt, listener);
    fh.watch(fooHtml, listener);
    fh.watch(barHtml, listener);
    fh.watch(foo, listener);
    fh.watch(bar, listener);
    fh.watch(txt, listener);
    fh.watch(html, listener);

    fs.getPath("/cwd/root/foo").createDirectory();
    fs.getPath("/cwd/root/bar").createDirectory();
    Path fooATxt = fs.getPath("/cwd/root/foo/a.txt");
    Path fooBTxt = fs.getPath("/cwd/root/foo/b.txt");
    Path barATxt = fs.getPath("/cwd/root/bar/a.txt");
    Path barBTxt = fs.getPath("/cwd/root/bar/b.txt");
    Path fooAHtml = fs.getPath("/cwd/root/foo/a.html");
    Path fooBHtml = fs.getPath("/cwd/root/foo/b.html");
    Path barAHtml = fs.getPath("/cwd/root/bar/a.html");
    Path barBHtml = fs.getPath("/cwd/root/bar/b.html");
    List<Path> allPaths = Arrays.asList(
        fooATxt, fooBTxt, barATxt, barBTxt,
        fooAHtml, fooBHtml, barAHtml, barBHtml);

    writeFile(fooATxt, "1");
    writeFile(fooBTxt, "1");
    writeFile(barATxt, "1");
    writeFile(barBTxt, "1");
    writeFile(fooAHtml, "1");
    writeFile(fooBHtml, "1");
    writeFile(barAHtml, "1");
    writeFile(barBHtml, "1");

    assertTrue(getLog().isEmpty());
    fh.update(allPaths);
    assertEquals(
        Joiner.on('\n').join(
            // foo{a,b}.txt changed
            "INFO: changed foo.txt",
            "INFO: changed foo",
            "INFO: changed .txt",
            // bar{a,b}.txt changed
            "INFO: changed bar.txt",
            "INFO: changed bar",
            // foo{a,b}.html changed
            "INFO: changed foo.html",
            "INFO: changed .html",
            // bar{a,b}.html changed
            "INFO: changed bar.html"),
            Joiner.on('\n').join(getLog()));
    getLog().clear();
    fh.update(allPaths);
    assertEquals("", Joiner.on('\n').join(getLog()));

    writeFile(fooBHtml, "2");
    fh.update(allPaths);
    assertEquals(
        Joiner.on('\n').join(
            "INFO: changed foo.html",
            "INFO: changed foo",
            "INFO: changed .html"),
        Joiner.on('\n').join(getLog()));
    getLog().clear();

    assertEquals(
        ""
        + "[**.html, **.txt, bar/**, bar/**.html, bar/**.txt,"
        + " foo/**, foo/**.html, foo/**.txt]",
        fh.unittestBackdoorDispatcherKeys());
    fh.unwatch(txt, listener);
    fh.unwatch(html, listener);
    assertEquals("", Joiner.on('\n').join(getLog()));
    writeFile(barAHtml, "2");
    fh.update(allPaths);
    assertEquals(
        Joiner.on('\n').join(
            "INFO: changed bar.html",
            "INFO: changed bar"),
        Joiner.on('\n').join(getLog()));
    getLog().clear();
    assertEquals(  // Make sure no leaks
        "[bar/**, bar/**.html, bar/**.txt, foo/**, foo/**.html, foo/**.txt]",
        fh.unittestBackdoorDispatcherKeys());

    fh.watch(html, listener);
    fh.watch(html, listener);
    assertEquals("", Joiner.on('\n').join(getLog()));
    writeFile(barAHtml, "3");
    fh.update(allPaths);
    assertEquals(
        Joiner.on('\n').join(
            "INFO: changed bar.html",
            "INFO: changed bar",
            "INFO: changed .html",
            "INFO: changed .html"),
        Joiner.on('\n').join(getLog()));
    getLog().clear();

    fh.unwatch(html, listener);
    assertEquals("", Joiner.on('\n').join(getLog()));
    writeFile(barAHtml, "4");
    fh.update(allPaths);
    assertEquals(
        Joiner.on('\n').join(
            "INFO: changed bar.html",
            "INFO: changed bar",
            "INFO: changed .html"),
        Joiner.on('\n').join(getLog()));
    getLog().clear();

    fh.unwatch(html, listener);
    assertEquals("", Joiner.on('\n').join(getLog()));
    writeFile(barAHtml, "4");  // No change
    fh.update(allPaths);
    assertEquals("", Joiner.on('\n').join(getLog()));
  }

  private String getHashStr(Path... paths) {
    return getHashStr(paths(paths));
  }

  private String getHashStr(List<Path> paths) {
    return getHash(paths).toHexString();
  }

  private Hash getHash(Path... paths) {
    return getHash(paths(paths));
  }

  private Hash getHash(List<Path> paths) {
    Hash.Builder hb = Hash.builder();
    fh.getHashes(paths, hb);
    return hb.build();
  }

  private void assertHash(String golden, List<Path> paths) {
    assertEquals(golden, getHashStr(paths));
  }

  private List<Path> paths(String... s) {
    Path[] paths = new Path[s.length];
    for (int i = paths.length; --i >= 0;) {
      paths[i] = fs.getPath(s[i]);
    }
    return paths(paths);
  }

  private List<Path> paths(Path... paths) { return Arrays.asList(paths); }
}
