package org.prebake.service.tools;

import org.prebake.fs.NonFileArtifact;

import javax.annotation.ParametersAreNonnullByDefault;

import com.sun.istack.internal.Nullable;

@ParametersAreNonnullByDefault
final class ToolImpl implements NonFileArtifact {
  final Tool tool;
  final String name;
  final int index;
  @Nullable ToolSignature sig;
  private boolean valid;

  ToolImpl(Tool tool, String name, int index) {
    this.tool = tool;
    this.name = name;
    this.index = index;
  }

  public boolean isValid() { return valid; }

  public void markValid(boolean valid) {
    synchronized (tool) {
      this.valid = valid;
      if (!valid) {
        sig = null;
        if (tool.validator != null) {
          tool.validator.cancel(false);
          tool.validator = null;
        }
      }
    }
  }
}
