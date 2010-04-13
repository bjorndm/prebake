package org.prebake.service.tools;

import org.prebake.fs.NonFileArtifact;

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
    synchronized (tool) {
      this.valid = valid;
      if (sig == null) { return; }
      if (!valid) { sig = null; }
    }
    tool.check();
  }
}
