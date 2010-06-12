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
import org.prebake.core.GlobRelation;
import org.prebake.core.GlobSet;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.core.MessageQueue;
import org.prebake.core.GlobRelation.Param;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.MobileFunction;
import org.prebake.js.YSON;
import org.prebake.js.YSONConverter;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  public final String name;
  public final Documentation help;
  public final GlobRelation filesAndParams;
  public final ImmutableList<Action> actions;
  public final boolean isIntermediate;
  public final MobileFunction bake;
  public final ImmutableMap<String, String> bindings;
  public final Path source;

  /**
   * @param name a unique name that identifies this product.  This is the name
   *    that will be used in with the build command, so
   *    <code>$ bake build cake</code> will build the product named "cake".
   * @param help optional documentation.
   * @param filesAndParams bundles the inputs, the set of files that need to be
   *    present when this product's actions are executed; with the outputs, the
   *    set of files that the product produces; with any parameters needed
   *    before this product is concrete enough to be included in a
   *    {@link Recipe}.
   * @param actions the commands to execute to produce the outputs from the
   *    inputs.
   * @param isIntermediate true if this product is required by other products
   *    but should not be explicitly built by the user.
   * @param bake null or a mobile function that, at bake time, receives a
   *    function that builds each action, and can choose to run some and not
   *    others, and reinterpret results.
   */
  public Product(
      String name, @Nullable Documentation help, GlobRelation filesAndParams,
      List<? extends Action> actions, boolean isIntermediate,
      @Nullable MobileFunction bake, Path source) {
    this(name, help, filesAndParams, actions, isIntermediate, bake,
         ImmutableMap.<String, String>of(), source);
  }

  public Product(
      String name, @Nullable Documentation help, GlobRelation filesAndParams,
      List<? extends Action> actions, boolean isIntermediate,
      @Nullable MobileFunction bake, Map<String, String> bindings,
      Path source) {
    assert name != null;
    assert filesAndParams != null;
    assert actions != null;
    assert source != null;
    this.name = name;
    this.help = help;
    this.filesAndParams = filesAndParams;
    this.actions = ImmutableList.copyOf(actions);
    this.isIntermediate = isIntermediate;
    this.bake = bake;
    this.bindings = ImmutableMap.copyOf(bindings);
    this.source = source;
  }

  public ImmutableGlobSet getInputs() { return filesAndParams.inputs; }

  public ImmutableGlobSet getOutputs() { return filesAndParams.outputs; }

  Product withName(String newName) {
    return new Product(
        newName, help, filesAndParams, actions, isIntermediate, bake, bindings,
        source);
  }

  public Product withoutNonBuildableInfo() {
    return new Product(
        name, null, filesAndParams, actions, false, bake, bindings, source);
  }

  /**
   * Returns a version with all data that can't be serialized to JSON stripped
   * out, so {@code withJsonOnly.toJson(myJsonSink)} is guaranteed to produce
   * valid JSON not YSON.
   */
  public Product withJsonOnly() {
    if (bake == null) { return this; }
    return new Product(
        name, help, filesAndParams, actions, isIntermediate, null, bindings,
        source);
  }

  /**
   * Returns a concrete product by binding inputs and outputs with the given
   * bindings.
   */
  public Product withParameterValues(Map<String, String> parameterValues) {
    if (parameterValues.isEmpty()) { return this; }
    ImmutableMap<String, String> bindings;
    {
      ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
      b.putAll(this.bindings);
      // Will fail if this.bindings and parameterValues have overlapping keys.
      b.putAll(parameterValues);
      bindings = b.build();
    }
    GlobRelation.Solution s = filesAndParams.withParameterValues(bindings);
    GlobRelation newFilesAndParams = new GlobRelation(s.inputs, s.outputs);
    ImmutableList.Builder<Action> newActions = ImmutableList.builder();
    for (Action a : actions) {
      newActions.add(a.withParameterValues(bindings));
    }
    return new Product(
        name, help, newFilesAndParams, newActions.build(), isIntermediate, bake,
        bindings, source);
  }

  /**
   * True iff this product has no free parameters, so can be used in a recipe
   * to build actual files.
   */
  public boolean isConcrete() { return filesAndParams.parameters.isEmpty(); }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Product)) { return false; }
    Product that = (Product) o;
    return this.isIntermediate == that.isIntermediate
        && this.name.equals(that.name)
        && Objects.equals(this.help, that.help)
        && this.actions.equals(that.actions)
        && this.filesAndParams.equals(that.filesAndParams)
        && this.bindings.equals(that.bindings)
        && Objects.equals(this.bake, that.bake);
  }

  @Override
  public int hashCode() {
    return name.hashCode() + 31 * (
        actions.hashCode() + 31 * (
            filesAndParams.hashCode() + 31 * (
                (isIntermediate ? 1 : 0) + 31 * (
                    bake != null ? bake.hashCode() : 0))));
  }

  /** Property names in the YSON representation. */
  public enum Field {
    help,
    inputs,
    outputs,
    actions,
    intermediate,
    bake,
    bindings,
    parameters,
    ;
  }

  public void toJson(JsonSink sink) throws IOException {
    GlobSet inputs = filesAndParams.inputs;
    GlobSet outputs = filesAndParams.outputs;
    sink.write("{").writeValue(Field.inputs).write(":");
    inputs.toJson(sink);
    sink.write(",").writeValue(Field.outputs).write(":");
    outputs.toJson(sink);
    sink.write(",").writeValue(Field.actions).write(":").writeValue(actions);
    if (!filesAndParams.parameters.isEmpty()) {
      sink.write(":").writeValue(Field.parameters).write(":[");
      boolean sawOne = false;
      for (GlobRelation.Param p : filesAndParams.parameters.values()) {
        if (sawOne) { sink.write(","); }
        sawOne = true;
        p.toJson(sink);
      }
      sink.write("]");
    }
    if (!bindings.isEmpty()) {
      sink.write(",").writeValue(Field.bindings)
          .write(":").writeValue(bindings);
    }
    if (isIntermediate) {
      sink.write(",").writeValue(Field.intermediate)
          .write(":").writeValue(isIntermediate);
    }
    if (help != null) {
      sink.write(",").writeValue(Field.help).write(":").writeValue(help);
    }
    if (bake != null) {
      sink.write(",").writeValue(Field.bake).write(":").writeValue(bake);
    }
    sink.write("}");
  }

  private static final YSONConverter<String> STRING_CONV
      = YSONConverter.Factory.withType(String.class);

  private static final YSONConverter<Map<String, Object>> PARAM_FIELDS_CONV
      = YSONConverter.Factory.mapConverter(String.class)
          .require(
              "name", YSONConverter.Factory.require(
                  STRING_CONV,
                  new Predicate<String>() {
                    public boolean apply(String name) {
                      return YSON.isValidIdentifier(name);
                    }
                    @Override public String toString() { return "a JS ident"; }
                  }))
          .optional(
              "values", YSONConverter.Factory.listConverter(STRING_CONV),
              ImmutableList.<String>of())
          .build();

  private static final YSONConverter<GlobRelation.Param> PARAM_CONV
      = new YSONConverter<GlobRelation.Param>() {
    public Param convert(Object ysonValue, MessageQueue problems) {
      Map<String, Object> fields = PARAM_FIELDS_CONV.convert(
          ysonValue, problems);
      if (fields == null) { return null; }
      String name = (String) fields.get("name");
      ImmutableSet.Builder<String> values = ImmutableSet.builder();
      for (Object value : (List<?>) fields.get("values")) {
        values.add((String) value);
      }
      return new GlobRelation.Param(name, values.build());
    }

    public String exampleText() { return PARAM_FIELDS_CONV.exampleText(); }
  };

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
          .optional(
              Field.bake.name(),
              YSONConverter.Factory.withType(MobileFunction.class), null)
          .optional(
              Field.bindings.name(),
              YSONConverter.Factory.mapConverter(STRING_CONV, STRING_CONV),
              ImmutableMap.<String, String>of())
          .optional(
              Field.parameters.name(),
              YSONConverter.Factory.listConverter(PARAM_CONV),
              ImmutableList.<GlobRelation.Param>of())
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
        List<Glob> inputGlobs = getList(fields.get(Field.inputs), Glob.class);
        List<Glob> outputGlobs = getList(fields.get(Field.outputs), Glob.class);
        ImmutableGlobSet inputs = inputGlobs != null
            ? ImmutableGlobSet.of(inputGlobs) : null;
        ImmutableGlobSet outputs = outputGlobs != null
            ? ImmutableGlobSet.of(outputGlobs) : null;
        if (inputs == null || outputs == null) {
          // Default missing (not empty) inputs and outputs to the union of
          // the actions' inputs and outputs.
          Set<Glob> inSet = inputs == null
              ? Sets.<Glob>newLinkedHashSet() : null;
          Set<Glob> outSet = outputs == null
              ? Sets.<Glob>newLinkedHashSet() : null;
          for (Action a : actions) {
            if (inSet != null) { for (Glob g : a.inputs) { inSet.add(g); } }
            if (outSet != null) { for (Glob g : a.outputs) { outSet.add(g); } }
          }
          if (inSet != null) { inputs = ImmutableGlobSet.of(inSet); }
          if (outSet != null) { outputs = ImmutableGlobSet.of(outSet); }
        }
        ImmutableList<GlobRelation.Param> params;
        {
          ImmutableList.Builder<GlobRelation.Param> b = ImmutableList.builder();
          for (Object p : (Iterable<?>) fields.get(Field.parameters)) {
            b.add((GlobRelation.Param) p);
          }
          params = b.build();
        }
        GlobRelation filesAndParams = new GlobRelation(inputs, outputs, params);
        MobileFunction bake = (MobileFunction) fields.get(Field.bake);
        Map<String, String> bindings;
        {
          ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
          for (Map.Entry<?, ?> e
               : ((Map<?, ?>) fields.get(Field.bindings)).entrySet()) {
            b.put((String) e.getKey(), (String) e.getValue());
          }
          bindings = b.build();
        }
        return new Product(
            name, (Documentation) fields.get(Field.help), filesAndParams,
            actions, Boolean.TRUE.equals(fields.get(Field.intermediate)), bake,
            bindings, source);
      }
      public String exampleText() { return MAP_CONV.exampleText(); }
    };
  }

  private static <T> List<T> getList(@Nullable Object o, Class<T> type) {
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
        && !m.containsKey(Field.bake.name())
        && m.containsKey(Action.Field.tool.name())
        && m.containsKey(Action.Field.options.name());
  }
}
