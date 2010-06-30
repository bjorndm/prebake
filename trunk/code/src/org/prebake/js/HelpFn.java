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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.UniqueTag;

/**
 * A JavaScript function that finds documentation attached to an object or
 * function.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
final class HelpFn extends BaseFunction {
  private final Console console;

  HelpFn(Scriptable scope, Console console) {
    this.console = console;
    ScriptRuntime.setFunctionProtoAndParent(this, scope);
  }

  @Override
  public Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args.length == 1) {
      @Nullable Object o = args[0];
      if (o instanceof Scriptable) {
        Object help = ScriptableObject.getProperty((Scriptable) o, "help_");
        if (UniqueTag.NOT_FOUND.equals(help)) { return Undefined.instance; }
        StringBuilder msg = new StringBuilder("Help: ");
        String name = null;
        if (o instanceof BaseFunction) {
          name = ((BaseFunction) o).getFunctionName();
          if ("".equals(name)) { name = null; }
        }
        if (name != null) { msg.append(name).append('\n'); }
        if (help instanceof String) {
          msg.append(help);
        } else if (help instanceof Scriptable) {
          Scriptable shelp = (Scriptable) help;
          Object summary = ScriptableObject.getProperty(
              shelp, Documentation.Field.summary.name());
          Object detail = ScriptableObject.getProperty(
              shelp, Documentation.Field.detail.name());
          Object contact = ScriptableObject.getProperty(
              shelp, Documentation.Field.contact.name());
          if (summary instanceof String
              && !(detail instanceof String
                  && ((String) detail).startsWith((String) summary))) {
            msg.append(summary);
            if (detail instanceof String) { msg.append('\n').append(detail); }
          } else if (detail instanceof String) {
            msg.append(detail);
          }
          if (contact instanceof String) {
            msg.append("\nContact: ").append(contact);
          }
        }
        console.logNoSub(Level.INFO, msg.toString());
      }
    }
    return Undefined.instance;
  }
  @Override
  public String getFunctionName() { return "help"; }

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
