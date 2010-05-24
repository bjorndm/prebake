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

import java.nio.file.Path;
import java.util.SortedMap;
import java.util.concurrent.Future;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.Maps;

@ParametersAreNonnullByDefault
final class Tool {
  final String toolName;
  final Path localName;
  final SortedMap<Integer, ToolImpl> impls = Maps.newTreeMap();
  final ToolBox toolBox;
  Future<ToolSignature> validator;

  Tool(String toolName, Path localName, ToolBox toolBox) {
    this.toolName = toolName;
    this.localName = localName;
    this.toolBox = toolBox;
  }

  void check() {
    ToolSignature sig = null;
    synchronized (this) {
      Integer index = impls.firstKey();
      if (index == null || (sig = impls.get(index).sig) == null) {
        if (validator != null) {
          validator.cancel(false);
          validator = null;
        }
      }
    }
    if (sig != null) {
      toolBox.listener.artifactChanged(sig);
    } else {
      toolBox.listener.artifactDestroyed(toolName);
    }
  }
}
