package org.prebake.service.tools;

import org.prebake.js.JsonSerializable;

/**
 * A description of a tool as seen by a plan file.
 *
 * @author mikesamuel@gmail.com
 */
public interface ToolSignature extends JsonSerializable {
  String getName();
  String getProductChecker();
  String getHelp();
  boolean isDeterministic();
}
