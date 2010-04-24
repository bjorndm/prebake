package org.prebake.service.tools;

/**
 * JavaScript object property names used in the output of a tool file.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public enum ToolDefProperty {
  /** The name of a documentation string. */
  help,
  /**
   * The name of a function that sanity checks a plan file product
   * that uses this rule.
   */
  check,
  /**
   * The name of a function that uses the tool to build a product.
   */
  fire,
  ;
}
