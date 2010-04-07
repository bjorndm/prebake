package org.prebake.service.plan;

import org.prebake.core.Glob;
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
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class Action implements JsonSerializable {
  public final String toolName;
  public final ImmutableList<Glob> inputs;
  public final ImmutableList<Glob> outputs;
  public final ImmutableMap<String, ?> options;

  public Action(
      String toolName, List<? extends Glob> inputs,
      List<? extends Glob> outputs, Map<String, ?> options) {
    this.toolName = toolName;
    this.inputs = ImmutableList.copyOf(inputs);
    this.outputs = ImmutableList.copyOf(outputs);
    this.options = ImmutableMap.copyOf(options);
    assert ObjUtil.isDeeplyImmutable(options);
  }

  public enum Field {
    tool,
    inputs,
    outputs,
    options,
    ;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue(Field.tool).write(":").writeValue(toolName)
        .write(",").writeValue(Field.inputs).write(":").writeValue(inputs)
        .write(",").writeValue(Field.outputs).write(":").writeValue(outputs);
    if (!options.isEmpty()) {
      sink.write(",").writeValue(Field.options).write(":").writeValue(options);
    }
    sink.write("}");
  }

  private static final YSONConverter<List<Glob>> GLOB_LIST_CONV
      = YSONConverter.Factory.listConverter(
          YSONConverter.Factory.withType(Glob.class));

  private static final YSONConverter<Map<Field, Object>> MAP_CONV
      = YSONConverter.Factory.<Field, Object>mapConverter(Field.class)
      .require(Field.tool.name(), YSONConverter.Factory.withType(String.class))
      .require(Field.inputs.name(), GLOB_LIST_CONV)
      .require(Field.outputs.name(), GLOB_LIST_CONV)
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
          (List<Glob>) (List<?>) fields.get(Field.inputs),
          (List<Glob>) (List<?>) fields.get(Field.outputs),
          (Map<String, ?>) fields.get(Field.options));
    }

    public String exampleText() { return MAP_CONV.exampleText(); }
  };

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }
}
