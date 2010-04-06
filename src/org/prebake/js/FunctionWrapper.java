package org.prebake.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

abstract class FunctionWrapper implements Scriptable, Function {
  protected final Function fn;

  FunctionWrapper(Function fn) { this.fn = fn; }

  public void delete(String arg0) { fn.delete(arg0); }
  public void delete(int arg0) { fn.delete(arg0); }
  public Object get(String arg0, Scriptable arg1) {
    return fn.get(arg0, arg1);
  }
  public Object get(int arg0, Scriptable arg1) {
    return fn.get(arg0, arg1);
  }
  public String getClassName() { return fn.getClassName(); }
  public Object getDefaultValue(Class<?> arg0) {
    return fn.getDefaultValue(arg0);
  }
  public Object[] getIds() { return fn.getIds(); }
  public Scriptable getParentScope() { return fn.getParentScope(); }
  public Scriptable getPrototype() { return fn.getPrototype(); }
  public boolean has(String arg0, Scriptable arg1) {
    return fn.has(arg0, arg1);
  }
  public boolean has(int arg0, Scriptable arg1) {
    return fn.has(arg0, arg1);
  }
  public boolean hasInstance(Scriptable arg0) {
    return fn.hasInstance(arg0);
  }
  public void put(String arg0, Scriptable arg1, Object arg2) {
    fn.put(arg0, arg1, arg2);
  }
  public void put(int arg0, Scriptable arg1, Object arg2) {
    fn.put(arg0, arg1, arg2);
  }
  public void setParentScope(Scriptable arg0) {
    fn.setParentScope(arg0);
  }
  public void setPrototype(Scriptable arg0) {
    fn.setPrototype(arg0);
  }
  public Object call(
      Context arg0, Scriptable arg1, Scriptable arg2, Object[] args) {
    return fn.call(arg0, arg1, arg2, args);
  }
  public Scriptable construct(Context arg0, Scriptable arg1, Object[] args) {
    return fn.construct(arg0, arg1, args);
  }
}
