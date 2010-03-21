package org.prebake.channel;

import org.prebake.core.DidYouMean;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Command {
  public enum Verb {
    handshake,
    build,
    files_changed,
    graph,
    plan,
    shutdown,
    sync,
    ;
  }

  public static final List<String> VERB_NAMES;
  static {
    List<String> names = new ArrayList<String>();
    for (Verb v : Verb.values()) { names.add(v.name()); }
    VERB_NAMES = Collections.unmodifiableList(names);
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
    List<Object> args = new ArrayList<Object>();
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
  public List<?> getParams() { return Collections.emptyList(); }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      toJson(new JsonSink(sb));
    } catch (IOException ex) {
      throw new RuntimeException("IOExcetion writing to StringBuilder", ex);
    }
    return sb.toString();
  }

  private static Set<String> toProducts(List<?> json) throws IOException {
    Set<String> products = new LinkedHashSet<String>();
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
    Set<Path> paths = new LinkedHashSet<Path>();
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
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public List<?> getParams() {
      return new ArrayList<String>(products);
    }
  }

  public static final class FilesChangedCommand extends Command {
    public final Set<Path> paths;

    public FilesChangedCommand(Set<Path> paths) {
      super(Verb.files_changed);
      this.paths = Collections.unmodifiableSet(
          new LinkedHashSet<Path>(paths));
    }

    @Override
    public List<String> getParams() {
      List<String> params = new ArrayList<String>();
      for (Path p : paths) { params.add(p.toString()); }
      return params;
    }
  }

  public static final class GraphCommand extends Command {
    public final Set<String> products;

    public GraphCommand(Set<String> products) {
      super(Verb.graph);
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public List<?> getParams() {
      return new ArrayList<String>(products);
    }
  }

  public static final class HandshakeCommand extends Command {
    public final String token;

    public HandshakeCommand(String token) {
      super(Verb.handshake);
      this.token = token;
    }

    @Override
    public List<String> getParams() {
      return Collections.singletonList(token);
    }
  }

  public static final class PlanCommand extends Command {
    public final Set<String> products;

    public PlanCommand(Set<String> products) {
      super(Verb.plan);
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public List<?> getParams() {
      return new ArrayList<String>(products);
    }
  }

  public static final class ShutdownCommand extends Command {
    public ShutdownCommand() { super(Verb.shutdown); }
  }

  public static final class SyncCommand extends Command {
    public SyncCommand() { super(Verb.sync); }
  }
}
