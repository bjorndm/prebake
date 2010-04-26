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
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Product implements JsonSerializable {
  // TODO: HIGH: add an optional bake method that gets as arguments functions
  // to run each action that can inspect the output and result code, and choose
  // to pipe input, conditionally execute, ignore or initialte failure, etc.
  public final String name;
  public final Documentation help;
  public final ImmutableList<Glob> inputs;
  public final ImmutableList<Glob> outputs;
  public final ImmutableList<Action> actions;
  public final boolean isIntermediate;
  public final Path source;

  /**
   * @param name a unique name that identifies this product.  This is the name
   *    that will be used in with the build command, so
   *    <code>$ bake build cake</code> will build the product named "cake".
   * @param help optional documentation.
   * @param inputs the set of files that need to be present when this product's
   *    actions are executed.
   * @param outputs the set of files that the product produces.
   * @param actions the commands to execute to produce the outputs from the
   *    inputs.
   * @param isIntermediate true if this product is required by other products
   *    but should not be explicitly built by the user.
   */
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

  public Product withoutNonBuildableInfo() {
    return new Product(name, null, inputs, outputs, actions, false, source);
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Product)) { return false; }
    Product that = (Product) o;
    if (this.isIntermediate != that.isIntermediate) { return false; }
    if (!this.name.equals(that.name)) { return false; }
    if (this.help == null ? that.help != null : !this.help.equals(that.help)) {
      return false;
    }
    return this.actions.equals(that.actions)
        && this.inputs.equals(that.inputs)
        && this.outputs.equals(that.outputs);
  }

  @Override
  public int hashCode() {
    return name.hashCode() + 31 * (
        actions.hashCode() + 31 * (
            inputs.hashCode() + 31 * (
                outputs.hashCode() + 31 * (
                    isIntermediate ? 1 : 0))));
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

  private static final YSONConverter<Map<Field, Object>> MAP_CONV
      = YSONConverter.Factory.mapConverter(Field.class)
          .optional(Field.help.name(), Documentation.CONVERTER, null)
          // Inputs and outputs are optional because reasonable defaults
          // can be inferred from the unions of the corresponding fields in
          // the actions.
          .optional(Field.inputs.name(), Glob.CONV, null)
          .optional(Field.outputs.name(), Glob.CONV, null)
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

  private static boolean looksLikeAction(@Nullable Object o) {
    if (!(o instanceof Map<?, ?>)) { return false; }
    Map<?, ?> m = (Map<?, ?>) o;
    return !m.containsKey(Field.actions.name())
        && m.containsKey(Action.Field.tool.name())
        && m.containsKey(Action.Field.options.name());
  }
}
