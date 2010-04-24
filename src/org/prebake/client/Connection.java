package org.prebake.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nonnull;

/**
 * Abstracts the important parts of a socket so that we can easily create test
 * stubs.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface Connection extends Closeable {
  @Nonnull InputStream getInputStream() throws IOException;
  @Nonnull OutputStream getOutputStream() throws IOException;
}
