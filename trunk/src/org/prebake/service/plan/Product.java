package org.prebake.service.plan;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A possible goal declared in a plan file.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/Product">wiki</a>
 */
@ParametersAreNonnullByDefault
public final class Product implements JsonSerializable {
  public final String name;
  public final Documentation help;
  public final ImmutableList<Glob> inputs;
  public final ImmutableList<Glob> outputs;
  public final ImmutableList<Action> actions;
  public final boolean isIntermediate;
  public final Path source;

  public Product(
      String name, @Nullable Documentation help,
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

  /** Property names in the YSON representation. */
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
          // Inputs and outputs are optional because reasonable defaults
          // can be inferred from the unions of the corresponding fields in
          // the actions.
          .optional(Field.inputs.name(), GLOB_LIST_CONV, null)
          .optional(Field.outputs.name(), GLOB_LIST_CONV, null)
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
      public @Nullable Product convert(
          @Nullable Object ysonValue, MessageQueue problems) {
        if (ysonValue instanceof List<?>) {
          List<?> list = (List<?>) ysonValue;
          if (list.isEmpty() || looksLikeAction(list.get(0))) {
            // Coerce a list of actions to a product.
           ysonValue = Collections.singletonMap(
               Field.actions.name(), ysonValue);
          }
        } else if (looksLikeAction(ysonValue)) {
          // Coerce a single action to an action.
          ysonValue = Collections.singletonMap(
              Field.actions.name(), Collections.singletonList(ysonValue));
        }
        Map<Field, ?> fields = MAP_CONV.convert(ysonValue, problems);
        if (problems.hasErrors()) { return null; }
        List<Action> actions = getList(fields.get(Field.actions), Action.class);
        List<Glob> inputs = getList(fields.get(Field.inputs), Glob.class);
        List<Glob> outputs = getList(fields.get(Field.outputs), Glob.class);
        if (inputs == null || outputs == null) {
          // Default missing (not empty) inputs and outputs to the union of
          // the actions' inputs and outputs.
          Set<Glob> inSet = inputs == null
              ? Sets.<Glob>newLinkedHashSet() : null;
          Set<Glob> outSet = outputs == null
              ? Sets.<Glob>newLinkedHashSet() : null;
          for (Action a : actions) {
            if (inSet != null) { inSet.addAll(a.inputs); }
            if (outSet != null) { outSet.addAll(a.outputs); }
          }
          if (inSet != null) { inputs = ImmutableList.copyOf(inSet); }
          if (outSet != null) { outputs = ImmutableList.copyOf(outSet); }
        }
        return new Product(
            name, (Documentation) fields.get(Field.help), inputs, outputs,
            actions, Boolean.TRUE.equals(fields.get(Field.intermediate)),
            source);
      }
      public String exampleText() { return MAP_CONV.exampleText(); }
    };
  }

  private static <T> List<T> getList(Object o, Class<T> type) {
    if (o == null) { return null; }
    ImmutableList.Builder<T> b = ImmutableList.builder();
    for (Object el : (List<?>) o) {
      b.add(type.cast(el));
    }
    return b.build();
  }

  private static boolean looksLikeAction(Object o) {
    if (!(o instanceof Map<?, ?>)) { return false; }
    Map<?, ?> m = (Map<?, ?>) o;
    return !m.containsKey(Field.actions.name())
        && m.containsKey(Action.Field.tool.name())
        && m.containsKey(Action.Field.options.name());
  }
}
