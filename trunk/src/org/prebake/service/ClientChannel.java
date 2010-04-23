package org.prebake.service;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.google.common.io.Closeables;
import com.google.common.io.Flushables;

/**
 * A log handler that channels log output to clients for the time their
 * operations are being processed.
 *
 * @author mikesamuel@gmail.com
 */
final class ClientChannel extends Handler implements Closeable {
  final Appendable out;

  ClientChannel(Appendable out) {
    this.out = out;
  }

  @Override public void close() {
    if (out instanceof Closeable) { Closeables.closeQuietly((Closeable) out); }
  }

  @Override public void flush() {
    if (out instanceof Flushable) { Flushables.flushQuietly((Flushable) out); }
  }

  @Override
  public void publish(LogRecord r) {
    try {
      out.append(r.getLevel().getName()).append(':')
          .append(MessageFormat.format(r.getMessage(), r.getParameters()))
          .append('\n');
    } catch (IOException ex) {
      // Ignore.  We can't recover but shouldn't interrupt existing operations.
      ex.printStackTrace();
    }
  }
}
