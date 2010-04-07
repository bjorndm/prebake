package org.prebake.js;

import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

@ParametersAreNonnullByDefault
final class Freezer {
  final Context cx;
  final Map<ScriptableObject, ScriptableObject> frozenCopies
      = Maps.newIdentityHashMap();
  Freezer(Context cx) { this.cx = cx; }

  <T extends ScriptableObject> T freeze(T obj) {
    for (Object name : obj.getAllIds()) {
      if (name instanceof Number) {
        int index = ((Number) name).intValue();
        obj.setAttributes(
            index,
            obj.getAttributes(index) | ScriptableObject.PERMANENT
            | ScriptableObject.READONLY);
      } else {
        String s = name.toString();
        obj.setAttributes(
            s,
            obj.getAttributes(s) | ScriptableObject.PERMANENT
            | ScriptableObject.READONLY);
      }
    }
    obj.preventExtensions();
    return obj;
  }

  boolean isFrozen(ScriptableObject obj) {
    if (obj.isExtensible()) { return false; }
    for (Object name : obj.getAllIds()) {
      if (!obj.isConst(name.toString())) { return false; }
    }
    return true;
  }

  Object frozenCopy(@Nullable Object obj) {
    if (!(obj instanceof ScriptableObject)) { return obj; }
    ScriptableObject so = (ScriptableObject) obj;
    ScriptableObject copy = frozenCopies.get(so);
    if (copy != null) { return copy; }
    if (so instanceof Function) {
      class DelegatingFunction extends ScriptableObject implements Function {
        private final Function fn;
        DelegatingFunction(Function fn) {
          super(fn.getParentScope(), fn.getPrototype());
          this.fn = fn;
        }
        @Override public String getClassName() { return fn.getClassName(); }
        public Object call(
            Context arg0, Scriptable arg1, Scriptable arg2, Object[] arg3) {
          return fn.call(arg0, arg1, arg2, arg3);
        }
        public Scriptable construct(
            Context arg0, Scriptable arg1, Object[] arg2) {
          return fn.construct(arg0, arg1, arg2);
        }
      }
      copy = new DelegatingFunction((Function) so);
    } else if (so instanceof NativeArray) {
      copy = new NativeArray(((NativeArray) so).getLength());
    } else {
      copy = new NativeObject();
    }
    frozenCopies.put(so, copy);
    boolean isFrozen = !so.isExtensible();
    if (so instanceof NativeArray) {
      for (Object name : so.getAllIds()) {
        if (name instanceof Number) {
          // TODO: freezing of array indices not supported
          isFrozen = false;
          int index = ((Number) name).intValue();
          Object value = ScriptableObject.getProperty(so, index);
          ScriptableObject.putProperty(copy, index, value);
        }
      }
    } else {
      for (Object name : so.getAllIds()) {
        String nameStr = name.toString();
        Object value = so.get(nameStr);
        int atts = so.getAttributes(nameStr);
        Object frozenValue = frozenCopy(value);
        int constBits = ScriptableObject.PERMANENT | ScriptableObject.READONLY;
        if (isFrozen
            && ((atts & constBits) != constBits || frozenValue != value)) {
          isFrozen = false;
        }
        ScriptableObject.defineProperty(
            copy, nameStr, frozenValue, atts | constBits);
      }
    }
    if (isFrozen) {
      frozenCopies.put(so, so);
      return so;
    } else {
      copy.preventExtensions();
      return copy;
    }
  }
}
