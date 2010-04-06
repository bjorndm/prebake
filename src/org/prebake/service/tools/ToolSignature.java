package org.prebake.service.tools;

import org.prebake.core.Documentation;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.YSON;
import org.prebake.js.YSONConverter;

import java.io.IOException;
import java.util.Map;

/**
 * A description of a tool as seen by a plan file.
 *
 * @author mikesamuel@gmail.com
 */
public final class ToolSignature implements JsonSerializable {
  public final String name;
  public final YSON.Lambda productChecker;
  public final Documentation help;
  public final boolean deterministic;

  public ToolSignature(
      String name, YSON.Lambda productChecker, Documentation help,
      boolean deterministic) {
    this.name = name;
    this.productChecker = productChecker;
    this.help = help;
    this.deterministic = deterministic;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("name").write(":").writeValue(name);
    if (help != null) {
      sink.write(",").writeValue("help").write(":")
          .writeValue(help.isDetailOnly() ? help.detailHtml : help);
    }
    if (productChecker != null) {
      sink.write(",").writeValue("check").write(":").writeValue(productChecker);
    }
    sink.write("}");
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  private static final YSONConverter<YSON.Lambda> LAMBDA
      = YSONConverter.Factory.withType(YSON.Lambda.class);
  private static final YSONConverter<Map<ToolDefProperty, Object>> MAP_CONV
      = YSONConverter.Factory
          .<ToolDefProperty, Object>mapConverter(ToolDefProperty.class)
          .optional(ToolDefProperty.help.name(), Documentation.CONVERTER, null)
          .optional(ToolDefProperty.check.name(), LAMBDA, null)
          .optional(ToolDefProperty.fire.name(), LAMBDA, null)
          .build();
  public static final YSONConverter<ToolSignature> converter(
      final String name, final boolean deterministic) {
    return new YSONConverter<ToolSignature>() {
      public ToolSignature convert(Object ysonValue, MessageQueue problems) {
        Map<ToolDefProperty, Object> map = MAP_CONV.convert(ysonValue, problems);
        if (map != null) {
          return new ToolSignature(
              name, (YSON.Lambda) map.get(ToolDefProperty.check),
              (Documentation) map.get(ToolDefProperty.help), deterministic);
        }
        return null;
      }
      public String exampleText() { return MAP_CONV.exampleText(); }
    };
  }
}
