package org.prebake.util;

import org.prebake.fs.FilePerms;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import junit.framework.TestCase;

public abstract class PbTestCase extends TestCase {
  private Logger logger;
  private Handler handler;
  private List<String> log;

  @Override
  protected void tearDown() throws Exception {
    if (logger != null) {
      logger.removeHandler(handler);
      logger = null;
      handler = null;
      log = null;
    }
  }

  protected List<String> getLog() { return log; }

  protected Logger getLogger(Level level) {
    if (logger == null) {
      log = Lists.newArrayList();
      logger = Logger.getLogger(getName());
      logger.addHandler(handler = new Handler() {
        @Override public void close() { log = null; }
        @Override public void flush() {}
        @Override
        public void publish(LogRecord r) {
          log.add(r.getSourceClassName() + ":" + r.getLevel() + ": "
              + MessageFormat.format(r.getMessage(), r.getParameters()));
          if (PbTestCase.this.logger.getLevel().intValue()
              < PbTestCase.this.logger.getParent().getLevel().intValue()) {
            System.err.println(
                r.getLevel() + " : "
                + MessageFormat.format(r.getMessage(), r.getParameters()));
          }
        }
      });
    }
    logger.setLevel(level);
    return logger;
  }

  protected static void rmDirTree(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) { rmDirTree(c); }
    }
    if (!f.delete()) {
      // We're just making a best effort.
    }
  }

  private static final FileAttribute<?>[] DIR_ATTRS
      = FilePerms.perms(0700, true);

  protected static void mkdirs(Path p) throws IOException {
    if (!p.exists()) {
      Path parent = p.getParent();
      if (parent != null) { mkdirs(parent); }
      p.createDirectory(DIR_ATTRS);
    }
  }

  protected void writeFile(Path p, String content) throws IOException {
    OutputStream out = p.newOutputStream(
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try {
      out.write(content.getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  protected File makeTempDir() throws IOException {
    File tempFile = File.createTempFile(getName(), ".dir");
    if (!tempFile.delete()) { throw new IOException(tempFile.toString()); }
    if (!tempFile.mkdirs()) { throw new IOException(tempFile.toString()); }
    return tempFile;
  }
}
