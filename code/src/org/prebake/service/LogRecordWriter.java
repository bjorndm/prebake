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

package org.prebake.service;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.LogRecord;

import javax.annotation.Nullable;

import com.google.common.io.Closeables;
import com.google.common.io.Flushables;

import org.prebake.js.Executor;

/**
 * Responsible for formatting log messages.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class LogRecordWriter implements Closeable, Flushable {
  final Appendable out;

  LogRecordWriter(Appendable out) {
    this.out = out;
  }

  public void close() {
    if (out instanceof Closeable) { Closeables.closeQuietly((Closeable) out); }
  }

  public void flush() {
    if (out instanceof Flushable) { Flushables.flushQuietly((Flushable) out); }
  }

  public void writeRecord(LogRecord r) {
    try {
      out.append(r.getLevel().getName()).append(':')
          .append(MessageFormat.format(r.getMessage(), r.getParameters()))
          .append('\n');
      publishThrowable(r.getThrown(), EMPTY_STACK);
    } catch (IOException ex) {
      // Ignore.  We can't recover but shouldn't interrupt existing operations.
      ex.printStackTrace();
    }
  }

  private static final StackTraceElement[] EMPTY_STACK
      = new StackTraceElement[0];

  private void publishThrowable(
      @Nullable Throwable th, StackTraceElement[] output)
      throws IOException {
    if (th == null) { return; }
    String s = th.toString();
    publishLine(s, 2);
    if (th instanceof Executor.AbnormalExitException) {
      String jsStack = ((Executor.AbnormalExitException) th).getScriptTrace();
      if (jsStack.length() != 0) {
        for (String jsStackLine : jsStack.split("[\r\n]+")) {
          if (jsStackLine.startsWith("\tat ")) {
            jsStackLine = jsStackLine.substring(4);
          }
          publishLine(jsStackLine, 4);
        }
      }
    } else {
      StackTraceElement[] els = th.getStackTrace();
      int nToSkip = 0;
      for (int i = els.length, j = output.length;
           --i >= 0 && --j >= 0; ++nToSkip) {
        if (!els[i].equals(output[j])) { break; }
      }
      int n = els.length - nToSkip;
      for (int i = 0; i < n; ++i) {
        StackTraceElement el = els[i];
        if (showStack(el)) {
          publishLine(el.toString(), 4);
          while (++i < n) { publishLine(els[i].toString(), 4); }
          break;
        }
      }
      if (nToSkip != 0) { publishLine("... " + nToSkip + " more", 4); }
      publishThrowable(th.getCause(), els);
    }
  }

  private void publishLine(String s, int indent) throws IOException {
    int pos = 0, n = s.length();
    for (int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      if (ch == '\r' || ch == '\n') {
        String chunk = s.substring(pos, i);
        publishChunk(chunk, indent);
        pos = i + 1;
      }
    }
    publishChunk(s.substring(pos, n), indent);
  }

  private void publishChunk(String oneLine, int indent) throws IOException {
    int pos = 0, n = oneLine.length();
    int lineLen = 80 - indent;
    while (pos + lineLen < n) {
      String line = oneLine.substring(pos, pos += lineLen - 2);
      indent(indent);
      out.append(line).append(" \\\n");
    }
    indent(indent);
    out.append(oneLine.substring(pos)).append('\n');
  }

  private static final String SIXTEEN_SPACES = "                ";
  static { assert SIXTEEN_SPACES.length() == 16; }
  private void indent(int indent) throws IOException {
    while (indent > 16) {
      out.append(SIXTEEN_SPACES);
      indent -= 16;
    }
    out.append(SIXTEEN_SPACES, 0, indent);
  }

  private static boolean showStack(StackTraceElement el) {
    String cn = el.getClassName();
    return !("org.mozilla.javascript.Parser".equals(cn)
             || "org.mozilla.javascript.DefaultErrorReporter".equals(cn));
  }
}
