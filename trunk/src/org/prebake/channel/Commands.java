package org.prebake.channel;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A bundle of commands from a single source.
 *
 * @author mikesamuel@gmail.com
 */
public final class Commands implements Iterable<Command> {
  private final List<Command> commands = new ArrayList<Command>();

  private Commands() {}

  public Iterator<Command> iterator() {
    return Collections.unmodifiableList(commands).iterator();
  }

  public static Commands fromJson(FileSystem fs, JsonSource src)
      throws IOException {
    Commands c = new Commands();
    src.expect("[");
    if (!src.check("]")) {
      do {
        c.commands.add(Command.fromJson(src, fs));
      } while (src.check(","));
      src.expect("]");
    }
    return c;
  }

  public static Commands valueOf(Iterable<Command> cmds) {
    Commands out = new Commands();
    for (Command c : cmds) { out.commands.add(c); }
    return out;
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
