package org.prebake.service.plan;

import org.prebake.core.Glob;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.util.ObjUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.List;

/**
 * A step in a recipe that involves invoking a {@link ToolBox.Tool tool}.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/BuildAction">wiki</a>
 * @author mikesamuel@gmail.com
 */
public final class Action implements JsonSerializable {
  public final String toolName;
  public final ImmutableList<Glob> inputs;
  public final ImmutableList<Glob> outputs;
  public final ImmutableMap<String, ?> options;

  public Action(
      String toolName, List<? extends Glob> inputs,
      List<? extends Glob> outputs, ImmutableMap<String, ?> options) {
    this.toolName = toolName;
    this.inputs = ImmutableList.copyOf(inputs);
    this.outputs = ImmutableList.copyOf(outputs);
    this.options = ImmutableMap.copyOf(options);
    assert ObjUtil.isDeeplyImmutable(options);
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("tool").write(":").writeValue(toolName)
        .write(",").writeValue("inputs").write(":").writeValue(inputs)
        .write(",").writeValue("outputs").write(":").writeValue(outputs)
        .write(",").writeValue("options").write(":").writeValue(options)
        .write("}");
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }
}
