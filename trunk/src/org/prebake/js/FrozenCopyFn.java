package org.prebake.js;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * A JavaScript function that returns a
 * {@link Freezer#frozenCopy(Object) frozen copy} of its input.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
final class FrozenCopyFn extends BaseFunction {
  // TODO: help documentation for this function.
  @Override
  public @Nullable Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    if (args.length != 1) { return Undefined.instance; }
    return new Freezer(cx).frozenCopy(args[0]);
  }
  @Override
  public String getFunctionName() { return "frozenCopy"; }
}
