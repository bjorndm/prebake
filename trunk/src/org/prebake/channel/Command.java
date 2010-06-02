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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Commands between the client and service.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see <a href="http://code.google.com/p/prebake/wiki/PreBakeCommand">wiki</a>
 */
@ParametersAreNonnullByDefault
public abstract class Command implements JsonSerializable {
  public enum Verb {
    handshake,
    auth_www,
    bake,
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

  /**
   * @oaram root the client root against which paths are resolved.
   */
  public static Command fromJson(JsonSource src, Path root) throws IOException {
    src.expect("[");
    String verb = src.expectString();
    Verb v;
    try {
      v = Verb.valueOf(verb);
    } catch (IllegalArgumentException ex) {
      throw new IOException(DidYouMean.toMessage(
          "Unknown verb " + verb, verb,
          VERB_NAMES.toArray(new String[VERB_NAMES.size()])), ex);
    }
    src.expect(",");
    /*Map<String, Object> options =*/ src.nextObject();
    List<Object> args = Lists.newArrayList();
    while (src.check(",")) {
      args.add(src.nextValue());
    }
    src.expect("]");
    switch (v) {
      case bake:          return new BakeCommand(toProducts(args));
      case auth_www:      return new AuthWwwCommand(toUriPath(args));
      case files_changed: return new FilesChangedCommand(toPaths(args, root));
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

  private static Set<Path> toPaths(List<?> json, Path clientRoot)
      throws IOException {
    Set<Path> paths = Sets.newLinkedHashSet();
    for (Object o : json) {
      if (o == null) { throw new IOException("Null path"); }
      if (!(o instanceof String)) {
        throw new IOException(o + " is not a string");
      }
      try {
        paths.add(clientRoot.resolve((String) o));
      } catch (InvalidPathException ex) {
        throw (IOException) new IOException("Bad path " + o).initCause(ex);
      }
    }
    return paths;
  }

  private static String toUriPath(List<?> json) throws IOException {
    if (json.isEmpty()) { return null; }
    if (json.size() == 1) {
      Object item = json.get(0);
      if (item == null || item instanceof String) { return (String) item; }
    }
    throw new IOException(
        JsonSink.stringify(json)
        + " should contain a single string URI path or be empty");
  }

  public static final class BakeCommand extends Command {
    public final Set<String> products;

    public BakeCommand(Set<String> products) {
      super(Verb.bake);
      this.products = ImmutableSet.copyOf(products);
    }

    @Override public Iterable<?> getParams() { return products; }
  }

  public static final class AuthWwwCommand extends Command {
    public final String continuePath;

    public AuthWwwCommand(@Nullable String continuePath) {
      super(Verb.auth_www);
      this.continuePath = continuePath;
    }

    @Override public Iterable<?> getParams() {
      if (continuePath == null) { return ImmutableList.of(); }
      return ImmutableList.of(continuePath);
    }
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
