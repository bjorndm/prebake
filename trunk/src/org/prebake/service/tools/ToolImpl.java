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

import org.prebake.fs.NonFileArtifact;
import org.prebake.service.HighLevelLog;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class ToolImpl implements NonFileArtifact {
  final Tool tool;
  final int index;
  @Nullable ToolSignature sig;
  private boolean valid;

  ToolImpl(Tool tool, int index) {
    this.tool = tool;
    this.index = index;
  }

  public boolean isValid() { return valid; }

  public void markValid(boolean valid) {
    if (!valid) {
      update(tool.toolBox.logs.highLevelLog.getClock().nanoTime(), null);
    }
    // Otherwise, expect the toolbox to call update subsequently.
  }

  void update(long t0, @Nullable ToolSignature newSig) {
    boolean needUpdate;
    ToolSignature oldSig;
    synchronized (tool) {
      this.valid = newSig != null;
      oldSig = this.sig;
      this.sig = newSig;
      needUpdate = oldSig != null && newSig == null;
    }
    if (needUpdate) { tool.toolBox.scheduleUpdate(); }

    if (newSig != null) {
      if (!newSig.equals(oldSig)) {
        tool.toolBox.listener.artifactChanged(sig);
      }
    } else if (oldSig != null) {
      tool.toolBox.listener.artifactDestroyed(tool.toolName);
    }
    HighLevelLog hll = tool.toolBox.logs.highLevelLog;
    hll.toolStatusChanged(t0, tool.toolName, newSig != null);
  }
}
