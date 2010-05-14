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

package org.prebake.js;

import java.io.IOException;

/**
 * A JavaScript function expression.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see <a href="http://code.google.com/p/prebake/wiki/MobileFunction">wiki</a>
 */
public final class MobileFunction implements JsonSerializable {
  private final String source;

  // TODO: define a withNameHint method that sets the function self name if
  // anonymous and use it to make lambdas show up better in stack traces.
  // So tool.fire for tool with name "cp" -> tool_cp_fire

  public MobileFunction(String source) {
    assert source != null;
    assert ((source.startsWith("function") && source.endsWith("}"))
            || source.startsWith("(function") && source.endsWith("})()"))
        : source;
    this.source = source;
  }

  /** The source code of a JavaScript function expression. */
  public String getSource() { return source; }

  @Override
  public String toString() { return source; }

  @Override
  public boolean equals(Object o) {
    return o instanceof MobileFunction
        && source.equals(((MobileFunction) o).source);
  }

  @Override
  public int hashCode() { return source.hashCode(); }

  public void toJson(JsonSink sink) throws IOException { sink.write(source); }
}
