package org.prebake.channel;

import org.prebake.core.DidYouMean;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class Command {
  public enum Verb {
    HANDSHAKE,
    BUILD,
    PLAN,
    CHANGES,
    GRAPH,
    SHUTDOWN,
    FILES_CHANGED,
    ;

    public final String ident;
    Verb() { this.ident = name().toLowerCase(Locale.ENGLISH); }
  }

  public final Verb verb;

  private Command(Verb verb) {
    this.verb = verb;
  }

  private static final Map<String, Verb> STRING_TO_VERB;
  static {
    Map<String, Verb> stringToVerb = new LinkedHashMap<String, Verb>();
    for (Verb v : Verb.values()) {
      stringToVerb.put(v.ident, v);
    }
    STRING_TO_VERB = Collections.unmodifiableMap(stringToVerb);
  }

  public static Command fromJson(JsonSource src, FileSystem fs)
      throws IOException {
    src.expect("[");
    String verb = src.expectString();
    Verb v = STRING_TO_VERB.get(verb);
    if (v == null) {
      throw new IOException(DidYouMean.toMessage(
          "Unknown verb " + verb, verb,
          STRING_TO_VERB.keySet().toArray(new String[0])));
    }
    src.expect(",");
    /*Map<String, Object> options =*/ src.nextObject();
    List<Object> args = new ArrayList<Object>();
    while (src.check(",")) {
      args.add(src.nextValue());
    }
    src.expect("]");
    switch (v) {
      case BUILD:         return new BuildCommand(toProducts(args));
      case CHANGES:       return new ChangesCommand();
      case FILES_CHANGED: return new FilesChangedCommand(toPaths(args, fs));
      case GRAPH:         return new GraphCommand(toProducts(args));
      case HANDSHAKE:     return new HandshakeCommand((String) args.get(0));
      case PLAN:          return new PlanCommand(toProducts(args));
      case SHUTDOWN:      return new ShutdownCommand();
      default: throw new Error(v.name());
    }
  }

  public abstract void toJson(JsonSink out) throws IOException;

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
      super(Verb.BUILD);
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}");
      for (String product : products) {
        out.write(",").writeValue(product);
      }
      out.write("]");
    }
  }

  public static final class ChangesCommand extends Command {
    public ChangesCommand() { super(Verb.CHANGES); }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}")
         .write("]");
    }
  }

  public static final class FilesChangedCommand extends Command {
    public final Set<Path> paths;

    public FilesChangedCommand(Set<Path> paths) {
      super(Verb.FILES_CHANGED);
      this.paths = Collections.unmodifiableSet(
          new LinkedHashSet<Path>(paths));
    }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}");
      for (Path path : paths) {
        out.write(",").writeValue(path);
      }
      out.write("]");
    }
  }

  public static final class GraphCommand extends Command {
    public final Set<String> products;

    public GraphCommand(Set<String> products) {
      super(Verb.BUILD);
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}");
      for (String product : products) {
        out.write(",").writeValue(product);
      }
      out.write("]");
    }
  }

  public static final class HandshakeCommand extends Command {
    public final String token;

    public HandshakeCommand(String token) {
      super(Verb.HANDSHAKE);
      this.token = token;
    }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}")
          .write(",").writeValue(token).write("]");
    }
  }

  public static final class PlanCommand extends Command {
    public final Set<String> products;

    public PlanCommand(Set<String> products) {
      super(Verb.BUILD);
      this.products = Collections.unmodifiableSet(
          new LinkedHashSet<String>(products));
    }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}");
      for (String product : products) {
        out.write(",").writeValue(product);
      }
      out.write("]");
    }
  }

  public static final class ShutdownCommand extends Command {
    public ShutdownCommand() { super(Verb.SHUTDOWN); }

    @Override
    public void toJson(JsonSink out) throws IOException {
      out.write("[").writeValue(verb.ident).write(",").write("{").write("}")
          .write("]");
    }
  }
}
