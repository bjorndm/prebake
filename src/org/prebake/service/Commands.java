package org.prebake.service;

import org.prebake.channel.Command;
import org.prebake.channel.JsonSource;

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
final class Commands implements Iterable<Command> {
  private final List<Command> commands = new ArrayList<Command>();

  private Commands() {}

  public Iterator<Command> iterator() {
    return Collections.unmodifiableList(commands).iterator();
  }

  static Commands fromJsonSource(FileSystem fs, JsonSource src)
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
}
