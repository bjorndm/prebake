package org.prebake.os;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

import com.google.common.base.Charsets;
import com.google.common.base.Function;


public class StubProcess extends Process {
  private boolean destroyed, completed;
  private final Function<String, String> streams;
  private final Callable<Integer> sideEffect;
  private final ByteArrayOutputStream outputStream;
  private ByteArrayInputStream inputStream;
  private int exitValue;

  public StubProcess(
      Function<String, String> streams, Callable<Integer> sideEffect) {
    this.streams = streams;
    this.sideEffect = sideEffect;
    this.outputStream = new ByteArrayOutputStream();
  }

  @Override public void destroy() { completed = destroyed = true; }

  @Override public int exitValue() {
    if (!completed) { throw new IllegalStateException(); }
    return destroyed ? -1 : exitValue;
  }

  @Override
  public InputStream getErrorStream() {
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public InputStream getInputStream() {
    // Simplification
    if (inputStream == null) { throw new IllegalStateException(); }
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return new FilterOutputStream(outputStream) {
      @Override public void close() throws IOException {
        String s = new String(outputStream.toByteArray(), Charsets.UTF_8);
        inputStream = new ByteArrayInputStream(
            streams.apply(s).getBytes(Charsets.UTF_8));
        super.close();
      }
    };
  }

  @Override
  public int waitFor() {
    if (!completed) {
      exitValue = -1;
      try {
        exitValue = sideEffect.call();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      completed = true;
    }
    return exitValue;
  }
}
