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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * A JavaScript function that returns a
 * {@link Freezer#frozenCopy(Object) frozen copy} of its input.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
final class FrozenCopyFn extends BaseFunction {
  // TODO: help documentation for this function.
  @Override
  public @Nullable Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args.length != 1) { return Undefined.instance; }
    return new Freezer().frozenCopy(args[0]);
  }
  @Override
  public String getFunctionName() { return "frozenCopy"; }

  // Instances cannot be serialized since console cannot be serialized.
  /** @param str unused */
  private void readObject(ObjectInputStream str) {
    throw new UnsupportedOperationException();
  }
  /** @param str unused */
  private void writeObject(ObjectOutputStream str) {
    throw new UnsupportedOperationException();
  }
}
