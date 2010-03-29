package org.prebake.fs;

import org.prebake.core.Hash;
import org.prebake.util.StubFileSystemProvider;

import com.google.common.collect.Maps;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.twmacinta.util.MD5;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import junit.framework.TestCase;

public class FileHashesTest extends TestCase {
  private FileSystem fs;
  private Environment env;
  private Logger logger;
  private File tempFile;
  private FileHashes fh;

  @Override
  protected void setUp() throws Exception {
    logger = Logger.getLogger(getName());
    fs = new StubFileSystemProvider("mfs").getFileSystem(
        URI.create("mfs:///#/cwd"));
    fs.getPath("/cwd/root").createDirectory();
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setAllowCreate(true);
    tempFile = File.createTempFile(getName(), ".bdb");
    tempFile.delete();
    tempFile.mkdirs();
    env = new Environment(tempFile, envConfig);
    fh = new FileHashes(env, fs.getPath("/cwd/root"), logger);
  }

  @Override
  protected void tearDown() throws Exception {
    fh.close();
    fh = null;
    env.close();
    env = null;
    fs.close();
    fs = null;
    rmDirTree(tempFile);
    tempFile = null;
    logger = null;
  }

  public final void testUpdates() throws Exception {
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

  public final void testFilesOutsideRoot() throws Exception {
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

  public final void testInvalidation() throws Exception {
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

  private String getHashStr(Path... paths) throws IOException {
    return getHashStr(paths(paths));
  }

  private String getHashStr(List<Path> paths) throws IOException {
    return MD5.asHex(getHash(paths).getBytes());
  }

  private Hash getHash(Path... paths) throws IOException {
    return getHash(paths(paths));
  }

  private Hash getHash(List<Path> paths) throws IOException {
    Hash.HashBuilder hb = Hash.builder();
    fh.getHashes(paths, hb);
    return hb.toHash();
  }

  private void assertHash(String golden, List<Path> paths) throws IOException {
    assertEquals(golden, getHashStr(paths));
  }

  private void writeFile(Path p, String content) throws IOException {
    OutputStream out = p.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      out.write(content.getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  private List<Path> paths(String... s) {
    Path[] paths = new Path[s.length];
    for (int i = paths.length; --i >= 0;) {
      paths[i] = fs.getPath(s[i]);
    }
    return paths(paths);
  }

  private List<Path> paths(Path... paths) { return Arrays.asList(paths); }

  private static void rmDirTree(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) { rmDirTree(c); }
    }
    f.delete();
  }
}
