package org.prebake.channel;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Iterator;

/**
 * A bundle of commands from a single source.
 *
 * @author mikesamuel@gmail.com
 */
public final class Commands implements Iterable<Command> {
  private final ImmutableList<Command> commands;

  private Commands(ImmutableList<Command> commands) {
    this.commands = commands;
  }

  public Iterator<Command> iterator() { return commands.iterator(); }

  public static Commands fromJson(FileSystem fs, JsonSource src)
      throws IOException {
    ImmutableList.Builder<Command> cmds = ImmutableList.builder();
    src.expect("[");
    if (!src.check("]")) {
      do {
        cmds.add(Command.fromJson(src, fs));
      } while (src.check(","));
      src.expect("]");
    }
    return new Commands(cmds.build());
  }

  public static Commands valueOf(Iterable<Command> cmds) {
    return new Commands(ImmutableList.copyOf(cmds));
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
