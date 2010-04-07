package org.prebake.js;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

@ParametersAreNonnullByDefault
final class FrozenCopyFn extends BaseFunction {
  @Override
  public @Nullable Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args.length != 1) { return Undefined.instance; }
    return new Freezer(cx).frozenCopy(args[0]);
  }
  @Override
  public String getFunctionName() { return "frozenCopy"; }
}
