package org.prebake.service.tools;

import java.nio.file.Path;
import java.util.SortedMap;
import java.util.concurrent.Future;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.collect.Maps;

@ParametersAreNonnullByDefault
final class Tool {
  final Path localName;
  final SortedMap<Integer, ToolImpl> impls = Maps.newTreeMap();
  Future<ToolSignature> validator;

  Tool(Path localName) { this.localName = localName; }
}
