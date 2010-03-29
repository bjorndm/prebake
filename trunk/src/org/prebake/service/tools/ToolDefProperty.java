package org.prebake.service.tools;

/**
 * Names used in the output of a tool file.
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
