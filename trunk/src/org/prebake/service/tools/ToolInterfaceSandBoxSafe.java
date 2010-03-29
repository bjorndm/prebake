package org.prebake.service.tools;

/**
 * Invoked from a tool JS file to describe the tool.
 *
 * @author mikesamuel@gmail.com
 */
public final class ToolInterfaceSandBoxSafe {
  private final String name;
  private String help;
  private String checker;

  ToolInterfaceSandBoxSafe(String name) { this.name = name; }

  public String getName() { return name; }
  public String getHelp() { return help; }
  public void setHelp(String help) { this.help = help; }
  public String getChecker() { return checker; }
  public void setChecker(Object checker) {
    this.checker = checker != null ? checker.toString() : null;
  }
}
