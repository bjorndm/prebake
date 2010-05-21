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
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * A log handler that channels log output to clients for the time their
 * operations are being processed.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class ClientChannel extends Handler implements Closeable {
  final LogRecordWriter out;

  ClientChannel(Appendable out) {
    this.out = new LogRecordWriter(out);
  }

  @Override public void close() { out.close(); }

  @Override public void flush() { out.flush(); }

  @Override public void publish(LogRecord r) { out.writeRecord(r); }
}
