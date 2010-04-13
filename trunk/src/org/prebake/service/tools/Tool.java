package org.prebake.service.tools;

import org.prebake.core.ArtifactListener;

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
  private final ArtifactListener<ToolSignature> listener;
  Future<ToolSignature> validator;

  Tool(
      String toolName, Path localName,
      ArtifactListener<ToolSignature> listener) {
    this.toolName = toolName;
    this.localName = localName;
    this.listener = listener;
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
      listener.artifactChanged(sig);
    } else {
      listener.artifactDestroyed(toolName);
    }
  }
}
