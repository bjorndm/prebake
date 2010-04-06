package org.prebake.service.plan;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A possible goal declared in a plan file.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/Product">wiki</a>
 */
public final class Product implements JsonSerializable {
  public final String name;
  public final Documentation help;
  public final ImmutableList<Glob> inputs;
  public final ImmutableList<Glob> outputs;
  public final ImmutableList<Action> actions;
  public final boolean isIntermediate;
  public final Path source;

  public Product(
      String name, Documentation help,
      List<? extends Glob> inputs, List<? extends Glob> outputs,
      List<? extends Action> actions, boolean isIntermediate,
      Path source) {
    assert name != null;
    assert inputs != null;
    assert outputs != null;
    assert actions != null;
    assert source != null;
    this.name = name;
    this.help = help;
    this.inputs = ImmutableList.copyOf(inputs);
    this.outputs = ImmutableList.copyOf(outputs);
    this.actions = ImmutableList.copyOf(actions);
    this.isIntermediate = isIntermediate;
    this.source = source;
  }

  Product withName(String newName) {
    return new Product(
        newName, help, inputs, outputs, actions, isIntermediate, source);
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  public enum Field {
    help,
    inputs,
    outputs,
    actions,
    intermediate,
    ;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue(Field.inputs).write(":").writeValue(inputs)
        .write(",").writeValue(Field.outputs).write(":").writeValue(outputs)
        .write(",").writeValue(Field.actions).write(":").writeValue(actions);
    if (isIntermediate) {
      sink.write(",").writeValue(Field.intermediate)
          .write(":").writeValue(isIntermediate);
    }
    if (help != null) {
      sink.write(",").writeValue(Field.help).write(":").writeValue(help);
    }
    sink.write("}");
  }

  private static final YSONConverter<List<Glob>> GLOB_LIST_CONV
      = YSONConverter.Factory.listConverter(
          YSONConverter.Factory.withType(Glob.class));
  private static final YSONConverter<Map<Field, Object>> MAP_CONV
      = YSONConverter.Factory.mapConverter(Field.class)
          .optional(Field.help.name(), Documentation.CONVERTER, null)
          .require(Field.inputs.name(), GLOB_LIST_CONV)
          .require(Field.outputs.name(), GLOB_LIST_CONV)
          .optional(
              Field.intermediate.name(),
              YSONConverter.Factory.withType(Boolean.class), false)
          .require(
              Field.actions.name(),
              YSONConverter.Factory.listConverter(Action.CONVERTER))
          .build();
  public static YSONConverter<Product> converter(
      final String name, final Path source) {
    return new YSONConverter<Product>() {
      @SuppressWarnings("unchecked")
      public Product convert(Object ysonValue, MessageQueue problems) {
        Map<Field, ?> fields = MAP_CONV.convert(ysonValue, problems);
        if (problems.hasErrors()) { return null; }
        return new Product(
            name, (Documentation) fields.get(Field.help),
            (List<Glob>) fields.get(Field.inputs),
            (List<Glob>) fields.get(Field.outputs),
            (List<Action>) fields.get(Field.actions),
            Boolean.TRUE.equals(fields.get(Field.intermediate)),
            source);
      }
      public String exampleText() { return MAP_CONV.exampleText(); }
    };
  }
}
