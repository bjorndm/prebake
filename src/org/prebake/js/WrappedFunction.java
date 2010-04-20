package org.prebake.js;

import org.prebake.core.Documentation;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.google.common.base.Function;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * A JavaScript function that finds documentation attached to an object or
 * function.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
final class WrappedFunction extends BaseFunction {
  private final Membrane membrane;
  private final Function<Object[], Object> body;
  private final String name;

  WrappedFunction(
      Membrane membrane, Function<Object[], Object> body, @Nullable String name,
      @Nullable Documentation doc) {
    this.membrane = membrane;
    this.name = name;
    this.body = body;
    ScriptRuntime.setFunctionProtoAndParent(this, membrane.scope);
    if (doc != null) {
      NativeObject helpObj = new NativeObject();
      ScriptableObject.putConstProperty(
          helpObj, Documentation.Field.summary.name(), doc.summaryHtml);
      ScriptableObject.putConstProperty(
          helpObj, Documentation.Field.detail.name(), doc.detailHtml);
      ScriptableObject.putConstProperty(
          helpObj, Documentation.Field.contact.name(), doc.contactEmail);
      new Freezer().freeze(helpObj);
      ScriptableObject.putProperty(this, "help_", helpObj);
    }
    new Freezer().freeze(this);
  }

  @Override
  public Object call(
      Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    Object[] membranedInputs = new Object[args.length];
    for (int i = 0, n = args.length; i < n; ++i) {
      membranedInputs[i] = membrane.fromJs(args[i]);
    }
    // TODO: properly translate arrays, and objects
    // to lists and maps respectively.
    return membrane.toJs(body.apply(args));
  }
  @Override
  public String getFunctionName() {
    return name != null ? name : super.getFunctionName();
  }

  // Instances cannot be serialized since console cannot be serialized.
  /** @param str unused */
  private void readObject(ObjectInputStream str) {
    throw new UnsupportedOperationException();
  }
  /** @param str unused */
  private void writeObject(ObjectOutputStream str) {
    throw new UnsupportedOperationException();
  }

  static final class Skeleton implements ScriptableSkeleton {
    final Function<Object[], Object> body;
    final String name;
    final Documentation help;

    Skeleton(Function<Object[], Object> body, String name, Documentation help) {
      this.body = body;
      this.name = name;
      this.help = help;
    }

    public BaseFunction fleshOut(Membrane membrane) {
      return new WrappedFunction(membrane, body, name, help);
    }
  }
}
