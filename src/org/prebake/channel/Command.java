package org.prebake.channel;

import org.prebake.core.DidYouMean;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.JsonSource;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commands between the client and service.
 *
 * @author mikesamuel@gmail.com
 * @see <a href="http://code.google.com/p/prebake/wiki/PreBakeCommand">wiki</a>
 */
public abstract class Command implements JsonSerializable {
  public enum Verb {
    handshake,
    build,
    files_changed,
    graph,
    plan,
    shutdown,
    sync,
    tool_help,
    ;
  }

  public static final List<String> VERB_NAMES;
  static {
    ImmutableList.Builder<String> names = ImmutableList.builder();
    for (Verb v : Verb.values()) { names.add(v.name()); }
    VERB_NAMES = names.build();
  }

  public final Verb verb;

  private Command(Verb verb) {
    this.verb = verb;
  }

  public static Command fromJson(JsonSource src, FileSystem fs)
      throws IOException {
    src.expect("[");
    String verb = src.expectString();
    Verb v = Verb.valueOf(verb);
    if (v == null) {
      throw new IOException(DidYouMean.toMessage(
          "Unknown verb " + verb, verb, VERB_NAMES.toArray(new String[0])));
    }
    src.expect(",");
    /*Map<String, Object> options =*/ src.nextObject();
    List<Object> args = Lists.newArrayList();
    while (src.check(",")) {
      args.add(src.nextValue());
    }
    src.expect("]");
    switch (v) {
      case build:         return new BuildCommand(toProducts(args));
      case files_changed: return new FilesChangedCommand(toPaths(args, fs));
      case graph:         return new GraphCommand(toProducts(args));
      case handshake:     return new HandshakeCommand((String) args.get(0));
      case plan:          return new PlanCommand(toProducts(args));
      case shutdown:      return new ShutdownCommand();
      case sync:          return new SyncCommand();
      case tool_help:     return new ToolHelpCommand();
    }
    throw new Error(v.name());
  }

  public final void toJson(JsonSink out) throws IOException {
    out.write("[").writeValue(verb.name()).write(",");
    out.writeValue(getOptions());
    for (Object o : getParams()) {
      out.write(",").writeValue(o);
    }
    out.write("]");
  }

  public Map<String, ?> getOptions() {
    return Collections.<String, Object>emptyMap();
  }
  public Iterable<?> getParams() { return Collections.emptyList(); }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      toJson(new JsonSink(sb));
    } catch (IOException ex) {
      Throwables.propagate(ex);  // IOExcetion writing to StringBuilder
    }
    return sb.toString();
  }

  private static Set<String> toProducts(List<?> json) throws IOException {
    Set<String> products = Sets.newLinkedHashSet();
    for (Object o : json) {
      if (o == null) { throw new IOException("Null product"); }
      if (!(o instanceof String)) {
        throw new IOException(o + " is not a string");
      }
      products.add((String) o);
    }
    return products;
  }

  private static Set<Path> toPaths(List<?> json, FileSystem fs)
      throws IOException {
    Set<Path> paths = Sets.newLinkedHashSet();
    for (Object o : json) {
      if (o == null) { throw new IOException("Null path"); }
      if (!(o instanceof String)) {
        throw new IOException(o + " is not a string");
      }
      try {
        paths.add(fs.getPath((String) o));
      } catch (InvalidPathException ex) {
        throw (IOException) new IOException("Bad path " + o).initCause(ex);
      }
    }
    return paths;
  }

  public static final class BuildCommand extends Command {
    public final Set<String> products;

    public BuildCommand(Set<String> products) {
      super(Verb.build);
      this.products = ImmutableSet.copyOf(products);
    }

    @Override public Iterable<?> getParams() { return products; }
  }

  public static final class FilesChangedCommand extends Command {
    public final Set<Path> paths;

    public FilesChangedCommand(Set<Path> paths) {
      super(Verb.files_changed);
      this.paths = ImmutableSet.copyOf(paths);
    }

    @Override
    public Iterable<String> getParams() {
      List<String> params = Lists.newArrayList();
      for (Path p : paths) { params.add(p.toString()); }
      return params;
    }
  }

  public static final class GraphCommand extends Command {
    public final Set<String> products;

    public GraphCommand(Set<String> products) {
      super(Verb.graph);
      this.products = ImmutableSet.copyOf(products);
    }

    @Override
    public Iterable<?> getParams() { return products; }
  }

  public static final class HandshakeCommand extends Command {
    public final String token;

    public HandshakeCommand(String token) {
      super(Verb.handshake);
      this.token = token;
    }

    @Override
    public Iterable<String> getParams() {
      return Collections.singletonList(token);
    }
  }

  public static final class PlanCommand extends Command {
    public final Set<String> products;

    public PlanCommand(Set<String> products) {
      super(Verb.plan);
      this.products = ImmutableSet.copyOf(products);
    }

    @Override
    public Iterable<?> getParams() { return products; }
  }

  public static final class ShutdownCommand extends Command {
    public ShutdownCommand() { super(Verb.shutdown); }
  }

  public static final class SyncCommand extends Command {
    public SyncCommand() { super(Verb.sync); }
  }

  public static final class ToolHelpCommand extends Command {
    public ToolHelpCommand() { super(Verb.tool_help); }
  }
}