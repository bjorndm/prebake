package org.prebake.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Connection extends Closeable {
  InputStream getInputStream() throws IOException;
  OutputStream getOutputStream() throws IOException;
}
