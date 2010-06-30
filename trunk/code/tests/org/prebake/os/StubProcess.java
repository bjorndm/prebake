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

package org.prebake.os;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Charsets;
import com.google.common.base.Function;

@ParametersAreNonnullByDefault
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
