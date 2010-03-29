package org.prebake.service.tools;

/**
 * A description of a tool as seen by a plan file.
 *
 * @author mikesamuel@gmail.com
 */
public interface ToolSignature {
  String getName();
  String getProductChecker();
  String getDoc();
  boolean isDeterministic();
}
