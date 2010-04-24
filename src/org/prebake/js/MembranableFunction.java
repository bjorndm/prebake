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

import org.prebake.core.Documentation;

import javax.annotation.Nullable;

import com.google.common.base.Function;

/**
 * Marker interface for a function that can pass across the JavaScript membrane
 * to be exposed as an object.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface MembranableFunction extends Function<Object[], Object> {
  /** A JavaScript identifier which will show up in stack traces. */
  @Nullable String getName();
  @Nullable Documentation getHelp();
  /** The minimum number of arguments needed by the function. */
  int getArity();
}
