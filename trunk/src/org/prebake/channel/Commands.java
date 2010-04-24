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

package org.prebake.channel;

import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.util.Iterator;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A bundle of commands from a single source.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Commands implements Iterable<Command>, JsonSerializable {
  private final ImmutableList<Command> commands;
  private final @Nullable OutputStream response;

  private Commands(
      ImmutableList<Command> commands, @Nullable OutputStream response) {
    this.commands = commands;
    this.response = response;
  }

  public Iterator<Command> iterator() { return commands.iterator(); }

  public OutputStream getResponse() { return response; }

  public static Commands fromJson(
      FileSystem fs, JsonSource src, @Nullable OutputStream response)
      throws IOException {
    ImmutableList.Builder<Command> cmds = ImmutableList.builder();
    src.expect("[");
    if (!src.check("]")) {
      do {
        cmds.add(Command.fromJson(src, fs));
      } while (src.check(","));
      src.expect("]");
    }
    return new Commands(cmds.build(), response);
  }

  public static Commands valueOf(
      Iterable<Command> cmds, @Nullable OutputStream response) {
    return new Commands(ImmutableList.copyOf(cmds), response);
  }

  public void toJson(JsonSink out) throws IOException {
    out.write("[");
    Iterator<Command> it = commands.iterator();
    if (it.hasNext()) {
      it.next().toJson(out);
      while (it.hasNext()) {
        out.write(",");
        it.next().toJson(out);
      }
    }
    out.write("]");
  }
}
