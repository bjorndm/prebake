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
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JavaScript function expression.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 * @see <a href="http://code.google.com/p/prebake/wiki/MobileFunction">wiki</a>
 */
public final class MobileFunction implements JsonSerializable {
  private final String source;

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

  private static final Pattern FUNCTION_NO_NAME = Pattern.compile(
      "^(\\(?function\\s*)(\\()");
  public MobileFunction withNameHint(String nameHint) {
    nameHint = Normalizer.normalize(nameHint, Normalizer.Form.NFC);
    assert YSON.isValidIdentifier(nameHint);
    Matcher m = FUNCTION_NO_NAME.matcher(source);
    if (!m.find()) { return this; }  // Source has a name or comments.
    return new MobileFunction(
        m.group(1) + " " + nameHint + source.substring(m.start(2)));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MobileFunction
        && source.equals(((MobileFunction) o).source);
  }

  @Override
  public int hashCode() { return source.hashCode(); }

  public void toJson(JsonSink sink) throws IOException { sink.write(source); }
}
