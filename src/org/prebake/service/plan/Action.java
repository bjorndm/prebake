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

import org.prebake.core.Glob;
import org.prebake.core.GlobSet;
import org.prebake.core.ImmutableGlobSet;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSONConverter;
import org.prebake.util.ObjUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A step in a recipe that involves invoking a
 * {@link org.prebake.service.tools.ToolSignature tool}.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/BuildAction">wiki</a>
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class Action implements JsonSerializable {
  // TODO: get rid of options map in json form and just use property names in
  // the action object.  { inputs: ..., outputs, ..., options: { x, ... } } =>
  // { inputs: ..., outputs: ..., x: ... }

  public final String toolName;
  public final ImmutableGlobSet inputs;
  public final ImmutableGlobSet outputs;
  public final ImmutableMap<String, ?> options;

  public Action(
      String toolName, GlobSet inputs, GlobSet outputs,
      Map<String, ?> options) {
    ImmutableMap<String, ?> optionsMap = ImmutableMap.copyOf(options);
    assert ObjUtil.isDeeplyImmutable(optionsMap);
    this.toolName = toolName;
    this.inputs = ImmutableGlobSet.copyOf(inputs);
    this.outputs = ImmutableGlobSet.copyOf(outputs);
    this.options = optionsMap;
  }

  /** Property names in the YSON representation. */
  public enum Field {
    tool,
    inputs,
    outputs,
    options,
    ;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue(Field.tool).write(":").writeValue(toolName)
        .write(",").writeValue(Field.inputs).write(":");
    inputs.toJson(sink);
    sink.write(",").writeValue(Field.outputs).write(":");
    outputs.toJson(sink);
    if (!options.isEmpty()) {
      sink.write(",").writeValue(Field.options).write(":").writeValue(options);
    }
    sink.write("}");
  }

  public Action withParameterValues(ImmutableMap<String, String> bindings) {
    ImmutableList.Builder<Glob> boundInputs = ImmutableList.builder();
    for (Glob g : inputs) { boundInputs.add(g.subst(bindings)); }
    ImmutableList.Builder<Glob> boundOutputs = ImmutableList.builder();
    for (Glob g : outputs) { boundOutputs.add(g.subst(bindings)); }
    return new Action(
        toolName, ImmutableGlobSet.of(boundInputs.build()),
        ImmutableGlobSet.of(boundOutputs.build()), options);
  }

  private static final YSONConverter<Map<Field, Object>> MAP_CONV
      = YSONConverter.Factory.<Field, Object>mapConverter(Field.class)
      .require(Field.tool.name(), YSONConverter.Factory.withType(String.class))
      .require(Field.inputs.name(), Glob.CONV)
      .require(Field.outputs.name(), Glob.CONV)
      .optional(Field.options.name(),
                YSONConverter.Factory.withDefault(
                    YSONConverter.Factory.withType(Map.class),
                    ImmutableMap.of()),
                ImmutableMap.of())
      .build();

  public static final YSONConverter<Action> CONVERTER
      = new YSONConverter<Action>() {
    @SuppressWarnings("unchecked")
    public Action convert(Object ysonValue, MessageQueue problems) {
      Map<Field, ?> fields = MAP_CONV.convert(ysonValue, problems);
      if (problems.hasErrors()) { return null; }
      return new Action(
          (String) fields.get(Field.tool),
          ImmutableGlobSet.of((List<Glob>) (List<?>) fields.get(Field.inputs)),
          ImmutableGlobSet.of((List<Glob>) (List<?>) fields.get(Field.outputs)),
          (Map<String, ?>) fields.get(Field.options));
    }

    public String exampleText() { return MAP_CONV.exampleText(); }
  };

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Action)) { return false; }
    Action that = (Action) o;
    return this.toolName.equals(that.toolName)
        && this.inputs.equals(that.inputs)
        && this.outputs.equals(that.outputs)
        && this.options.equals(that.options);
  }

  @Override
  public int hashCode() {
    return toolName.hashCode()
        + 31 * (inputs.hashCode()
                + 31 * (outputs.hashCode()
                        + 31 * options.hashCode()));
  }
}
