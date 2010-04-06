package org.prebake.js;

import org.prebake.core.Documentation;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

final class HelpFn extends BaseFunction {
  private final Console console;

  HelpFn(Console console) { this.console = console; }

  @Override
  public Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args.length == 1) {
      Object o = args[0];
      if (o instanceof Scriptable) {
        Object help = ScriptableObject.getProperty((Scriptable) o, "help_");
        StringBuilder msg = new StringBuilder("Help: ");
        if (help instanceof String) {
          msg.append(help);
        } else if (help instanceof Scriptable) {
          Scriptable shelp = (Scriptable) help;
          Object summary = ScriptableObject.getProperty(
              shelp, Documentation.Field.summary.name());
          Object detail = ScriptableObject.getProperty(
              shelp, Documentation.Field.detail.name());
          Object contact = ScriptableObject.getProperty(
              shelp, Documentation.Field.contact.name());
          if (summary instanceof String
              && !(detail instanceof String
                  && ((String) detail).startsWith((String) summary))) {
            msg.append(summary);
            if (detail instanceof String) { msg.append('\n').append(detail); }
          } else if (detail instanceof String) {
            msg.append(detail);
          }
          if (contact instanceof String) {
            msg.append("\nContact: ").append(contact);
          }
          console.log(msg.toString().replace("%", "%%"));
        }
      }
    }
    return Undefined.instance;
  }
  @Override
  public String getFunctionName() { return "help"; }
}
