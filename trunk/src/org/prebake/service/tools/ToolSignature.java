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

package org.prebake.service.tools;

import org.prebake.core.Documentation;
import org.prebake.core.MessageQueue;
import org.prebake.js.JsonSerializable;
import org.prebake.js.JsonSink;
import org.prebake.js.MobileFunction;
import org.prebake.js.YSONConverter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A description of a tool as seen by a plan file.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public final class ToolSignature implements JsonSerializable {
  public final String name;
  public final @Nullable MobileFunction productChecker;
  public final @Nullable Documentation help;
  public final boolean deterministic;

  public ToolSignature(
      String name, @Nullable MobileFunction productChecker,
      @Nullable Documentation help, boolean deterministic) {
    this.name = name;
    this.productChecker = productChecker;
    this.help = help;
    this.deterministic = deterministic;
  }

  public void toJson(JsonSink sink) throws IOException {
    sink.write("{").writeValue("name").write(":").writeValue(name);
    if (help != null) {
      sink.write(",").writeValue(ToolDefProperty.help.name()).write(":")
          .writeValue(help.isDetailOnly() ? help.detailHtml : help);
    }
    if (productChecker != null) {
      sink.write(",").writeValue(ToolDefProperty.check.name())
          .write(":").writeValue(productChecker);
    }
    sink.write("}");
  }

  @Override
  public String toString() {
    return JsonSerializable.StringUtil.toString(this);
  }

  @Override public boolean equals(Object o) {
    if (!(o instanceof ToolSignature)) { return false; }
    ToolSignature that = (ToolSignature) o;
    return this.deterministic == that.deterministic
        && this.name.equals(that.name)
        && Objects.equals(this.productChecker, that.productChecker)
        && Objects.equals(this.help, that.help);
  }

  @Override public int hashCode() { return name.hashCode(); }

  private static final YSONConverter<MobileFunction> FN
      = YSONConverter.Factory.withType(MobileFunction.class);
  private static final YSONConverter<Map<ToolDefProperty, Object>> MAP_CONV
      = YSONConverter.Factory
          .<ToolDefProperty, Object>mapConverter(ToolDefProperty.class)
          .optional(ToolDefProperty.help.name(), Documentation.CONVERTER, null)
          .optional(ToolDefProperty.check.name(), FN, null)
          .optional(ToolDefProperty.fire.name(), FN, null)
          .build();
  public static final YSONConverter<ToolSignature> converter(
      final String name, final boolean deterministic) {
    return new YSONConverter<ToolSignature>() {
      public @Nullable ToolSignature convert(
          @Nullable Object ysonValue, MessageQueue problems) {
        Map<ToolDefProperty, ?> map = MAP_CONV.convert(ysonValue, problems);
        if (map != null) {
          return new ToolSignature(
              name, (MobileFunction) map.get(ToolDefProperty.check),
              (Documentation) map.get(ToolDefProperty.help), deterministic);
        }
        return null;
      }
      public String exampleText() { return MAP_CONV.exampleText(); }
    };
  }
}
