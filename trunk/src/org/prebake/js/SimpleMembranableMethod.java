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

import javax.annotation.Nullable;

/**
 * A convenience for creating membranable methods as inner classes.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class SimpleMembranableMethod extends SimpleMembranableFunction
    implements MembranableMethod {

  public SimpleMembranableMethod(
      String docDetails,
      String name, @Nullable String outputType, String... formals) {
    super(docDetails, name, outputType, formals);
  }
}
