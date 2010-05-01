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

import java.io.File;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Extra environment available to builtin tools to let them perform certain
 * operations more efficiently by using the existing JVM.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class BuiltinToolHooks {
  private static final ImmutableMap<String, ImmutableMap<String, ?>> BY_TOOL;
  static {
    // Make classpath parts absolute so we can use it to spawn external
    // java processes that have access to the same classes as this JVM.
    List<String> classpath = Lists.newArrayList();
    for (String classpathPart
         : System.getProperty("java.class.path").split(File.pathSeparator)) {
      classpath.add(new File(classpathPart).getAbsolutePath());
    }
    BY_TOOL = ImmutableMap.<String, ImmutableMap<String, ?>>of(
        "junit", ImmutableMap.of(
            "java_classpath",
            Joiner.on(File.pathSeparatorChar).join(classpath)));
  }

  public static ImmutableMap<String, ?> extraEnvironmentFor(
      String builtinToolName) {
    ImmutableMap<String, ?> extraEnv = BY_TOOL.get(builtinToolName);
    if (extraEnv != null) { return extraEnv; }
    return ImmutableMap.of();
  }
}
