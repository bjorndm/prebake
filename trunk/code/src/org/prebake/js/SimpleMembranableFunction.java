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

import com.google.common.base.Joiner;

/**
 * A convenience for creating membranable functions as inner classes.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class SimpleMembranableFunction implements MembranableFunction {
  private final String name;
  private final Documentation help;
  private final int arity;

  public SimpleMembranableFunction(
      String docDetails,
      String name, @Nullable String outputType, String... formals) {
    this.name = name;
    int sigArity = formals.length;
    this.arity = sigArity == 0
        ? 0 : sigArity - (formals[sigArity - 1].endsWith("...") ? 1 : 0);
    StringBuilder sig = new StringBuilder();
    sig.append(name).append('(');
    Joiner.on(", ").appendTo(sig, formals);
    sig.append(')');
    if (outputType != null) { sig.append(" => ").append(outputType); }
    this.help = new Documentation(
        sig.toString(), docDetails, "prebake-discuss@googlegroups.com");
  }

  public final int getArity() { return arity; }

  public final Documentation getHelp() { return help; }

  public final String getName() { return name; }
}
