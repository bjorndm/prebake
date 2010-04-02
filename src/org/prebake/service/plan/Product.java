package org.prebake.service.plan;

import org.prebake.core.Documentation;
import org.prebake.core.Glob;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

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

  public Product(
      String name, Documentation help,
      List<? extends Glob> inputs, List<? extends Glob> outputs,
      List<? extends Action> actions, boolean isIntermediate) {
    assert name != null;
    assert inputs != null;
    assert outputs != null;
    assert actions != null;
    this.name = name;
    this.help = help;
    this.inputs = ImmutableList.copyOf(inputs);
    this.outputs = ImmutableList.copyOf(outputs);
    this.actions = ImmutableList.copyOf(actions);
    this.isIntermediate = isIntermediate;
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("name").write(":").writeValue(name)
        .write(",").writeValue("help").write(":").writeValue(help)
        .write(",").writeValue("inputs").write(":").writeValue(inputs)
        .write(",").writeValue("outputs").write(":").writeValue(outputs)
        .write(",").writeValue("actions").write(":").writeValue(actions)
        .write("}");
  }
}
