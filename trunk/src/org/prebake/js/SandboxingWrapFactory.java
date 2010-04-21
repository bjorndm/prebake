package org.prebake.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;

class SandboxingWrapFactory extends WrapFactory {
  @Override
  public Object wrap(
      Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
    // Deny reflective access up front.  This should not be triggered due
    // to getter filtering, but let's be paranoid.
    if (javaObject != null
        && (javaObject instanceof Class<?>
            || javaObject instanceof ClassLoader
            || "java.lang.reflect".equals(
                javaObject.getClass().getPackage().getName()))) {
      return Context.getUndefinedValue();
    }
    // Make java arrays behave like native JS arrays.
    // This breaks EQ, but is better than the alternative.
    if (javaObject instanceof Object[]) {
      Object[] javaArray = (Object[]) javaObject;
      int n = javaArray.length;
      Object[] wrappedElements = new Object[n];
      Class<?> compType = javaArray.getClass().getComponentType();
      for (int i = n; --i >= 0;) {
        wrappedElements[i] = wrap(cx, scope, javaArray[i], compType);
      }
      NativeArray jsArray = new NativeArray(wrappedElements);
      jsArray.setPrototype(ScriptableObject.getClassPrototype(scope, "Array"));
      jsArray.setParentScope(scope);
      return jsArray;
    }
    return super.wrap(cx, scope, javaObject, staticType);
  }

  @Override
  public Scriptable wrapAsJavaObject(
      Context cx, Scriptable scope, Object javaObject, Class<?> staticType) {
    return new WrappedJavaObject(scope, javaObject, staticType);
  }

  private static final class WrappedJavaObject extends NativeJavaObject {
    WrappedJavaObject(
        Scriptable scope, Object javaObject, Class<?> staticType) {
      super(scope, javaObject, staticType);
    }
    @Override
    public Object get(String name, Scriptable start) {
      // Deny access to all members of the base Object class since
      // some of them enable reflection, and the others are mostly for
      // serialization and timing which should not be accessible.
      // The codeutopia implementation only blacklists getClass.
      if (RhinoExecutor.OBJECT_CLASS_MEMBERS.contains(name)) {
        return NOT_FOUND;
      }
      return super.get(name, start);
    }
  }
}
