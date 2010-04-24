package org.prebake.js;

import org.prebake.util.WeakIdentityHashMap;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

final class Membrane {
  private final Map<Object, Object> membrane
      = new WeakIdentityHashMap<Object, Object>(Object.class);
  private final Context cx;
  final Scriptable scope;


  Membrane(Context cx, Scriptable scope) {
    this.cx = cx;
    this.scope = scope;
  }

  private static final Set<Class<?>> UNWRAPPED = ImmutableSet.<Class<?>>of(
      String.class, Float.class, Double.class, Integer.class, Long.class,
      Short.class, Byte.class, Character.class, Boolean.class);

  Object fromJs(@Nullable Object o) {
    if (o == null) { return null; }
    Class<?> cl = o.getClass();
    if (UNWRAPPED.contains(cl)) { return o; }
    Object otherSide = membrane.get(o);
    if (o instanceof Undefined) { return null; }
    if (otherSide != null) { return otherSide; }
    if (o instanceof NativeArray) {
      return makeListWrapper((NativeArray) o);
    }
    if (o instanceof BaseFunction) {
      return makeFnWrapper((BaseFunction) o);
    }
    if (o instanceof ScriptableObject) {
      return makeMapWrapper((ScriptableObject) o);
    }
    return makeOpaqueWrapper(o);
  }

  Object toJs(@Nullable Object o) {
    if (o == null) { return null; }
    Class<?> cl = o.getClass();
    if (UNWRAPPED.contains(cl)) { return o; }
    Object otherSide = membrane.get(o);
    if (otherSide != null) { return otherSide; }
    if (o instanceof ScriptableSkeleton) {
      return wrap(o, ((ScriptableSkeleton) o).fleshOut(this));
    }
    if (cl.isArray()) { return makeArrayWrapper(o); }
    if (o instanceof List<?>) {
      return makeListWrapper(
          (List<?>) o,
          o instanceof MembranableList ? ((MembranableList) o) : null);
    }
    if (o instanceof Map<?, ?>) {
      return makeObjWrapper(
          (Map<?, ?>) o,
          o instanceof MembranableMap ? (MembranableMap) o : null);
    }
    if (o instanceof MembranableFunction) {
      return makeFunctionWrapper((MembranableFunction) o);
    }
    return makeOpaqueWrapper(o);
  }

  private List<?> makeListWrapper(final NativeArray arr) {
    return wrap(arr, new AbstractList<Object>() {
      @Override
      public boolean add(Object item) {
        int len = size();
        ScriptableObject.putProperty(arr, len, toJs(item));
        return true;
      }

      @Override
      public void add(int pos, Object item) {
        addAll(pos, Collections.singleton(item));
      }

      @Override
      public boolean addAll(int pos, Collection<? extends Object> toAdd) {
        int nToAdd = toAdd.size();
        if (nToAdd == 0) { return false; }
        int len = size();
        for (int i = len; --i >= pos;) {
          ScriptableObject.putProperty(
              arr, i + nToAdd, ScriptableObject.getProperty(arr, i));
        }
        Iterator<?> it = toAdd.iterator();
        for (int i = pos, end = pos + nToAdd; i < end; ++i) {
          ScriptableObject.putProperty(arr, i, toJs(it.next()));
        }
        return true;
      }

      @Override
      public void clear() {
        ScriptableObject.putProperty(arr, "length", 0);
      }

      @Override
      public Object get(int index) {
        return fromJs(ScriptableObject.getProperty(arr, index));
      }

      @Override public boolean isEmpty() { return size() == 0; }

      @Override
      public Object remove(int index) {
        int len = size();
        if (index < 0 || index >= len) {
          throw new IndexOutOfBoundsException();
        }
        Object result = fromJs(ScriptableObject.getProperty(arr, index));
        for (int i = index + 1; i < len; ++i) {
          ScriptableObject.putProperty(
              arr, i - 1, ScriptableObject.getProperty(arr, i));
        }
        ScriptableObject.putProperty(arr, "length", len - 1);
        return result;
      }

      @Override
      public Object set(int pos, Object item) {
        Object old = get(pos);
        ScriptableObject.putProperty(arr, pos, toJs(item));
        return old;
      }

      @Override
      public int size() {
        Object len = ScriptableObject.getProperty(arr, "length");
        return ((Number) len).intValue();
      }
    });
  }

  private Function<Object[], Object> makeFnWrapper(final BaseFunction fn) {
    return wrap(fn, new Function<Object[], Object>() {
      public Object apply(Object[] javaArgs) {
        Object[] jsArgs = new Object[javaArgs.length];
        for (int i = jsArgs.length; --i >= 0;) {
          jsArgs[i] = toJs(javaArgs[i]);
        }
        return fromJs(fn.call(cx, scope, scope, jsArgs));
      }
    });
  }

  private Map<String, Object> makeMapWrapper(final ScriptableObject obj) {
    return wrap(obj, new AbstractMap<String, Object>() {
      @Override
      public Set<Map.Entry<String, Object>> entrySet() {
        final Set<String> props;
        {
          ImmutableSet.Builder<String> b = ImmutableSet.<String>builder();
          for (Object prop : ScriptableObject.getPropertyIds(obj)) {
            b.add(prop.toString());
          }
          props = b.build();
        }
        return new AbstractSet<Map.Entry<String, Object>>() {
          @Override
          public Iterator<Map.Entry<String, Object>> iterator() {
            return new Iterator<Map.Entry<String, Object>>() {
              Iterator<String> names = props.iterator();
              String lastName;

              public boolean hasNext() {
                return names.hasNext();
              }

              public Map.Entry<String, Object> next() {
                lastName = names.next();
                return new Map.Entry<String, Object>() {
                  String key = lastName;
                  public String getKey() { return key; }
                  public Object getValue() {
                    return fromJs(ScriptableObject.getProperty(obj, key));
                  }
                  public Object setValue(Object javaVal) {
                    Object old = getValue();
                    ScriptableObject.putProperty(obj, key, toJs(javaVal));
                    return old;
                  }
                };
              }

              public void remove() {
                if (lastName == null) { throw new IllegalStateException(); }
                ScriptableObject.deleteProperty(obj, lastName);
                lastName = null;
              }
            };
          }

          @Override public int size() { return props.size(); }
        };
      }

      @Override
      public boolean containsKey(Object key) {
        if (!(key instanceof String)) { return false; }
        return ScriptableObject.hasProperty(obj, (String) key);
      }

      @Override
      public Object get(Object key) {
        if (!(key instanceof String)) { return null; }
        return fromJs(ScriptableObject.getProperty(obj, (String) key));
      }

      @Override
      public Object put(String key, Object value) {
        Object old = fromJs(ScriptableObject.getProperty(obj, key));
        ScriptableObject.putProperty(obj, key, toJs(value));
        return old;
      }

      @Override
      public Object remove(Object key) {
        if (!(key instanceof String)) { return null; }
        Object old = fromJs(ScriptableObject.getProperty(obj, (String) key));
        ScriptableObject.deleteProperty(obj, (String) key);
        return old;
      }
    });
  }

  static final class OpaqueWrapper {
    @Override public String toString() { return "[object Object]"; }
  }

  private Object makeOpaqueWrapper(final Object o) {
    return wrap(o, new OpaqueWrapper());
  }

  private static final Map<Class<?>, Object> ZERO_VALUES
      = ImmutableMap.<Class<?>, Object>builder()
          .put(Boolean.TYPE, Boolean.FALSE)
          .put(Byte.TYPE, Byte.valueOf((byte) 0))
          .put(Character.TYPE, Character.valueOf('\0'))
          .put(Double.TYPE, 0d)
          .put(Float.TYPE, 0f)
          .put(Integer.TYPE, Integer.valueOf(0))
          .put(Long.TYPE, 0L)
          .put(Short.TYPE, Short.valueOf((short) 0))
          .build();

  private Scriptable makeArrayWrapper(final Object array) {
    return wrap(array, new Scriptable() {
      int n = Array.getLength(array);
      Class<?> cType = array.getClass().getComponentType();

      private int index(String k) {
        try {
          return Integer.parseInt(k, 10);
        } catch (NumberFormatException ex) {
          return -1;
        }
      }

      public void delete(String k) {
        delete(index(k));
      }
      public void delete(int i) {
        if (i < 0 || i >= n) { return; }  // delete fails silently in JS
        Array.set(array, i, ZERO_VALUES.get(cType));
      }

      public Object get(String k, Scriptable s) {
        if ("length".equals(k)) { return n; }
        int index = index(k);
        return index >= 0 ? get(index, s) : getPrototype().get(k, s);
      }

      public Object get(int i, Scriptable s) {
        if (i < 0 || i >= n) { return Undefined.instance; }
        return toJs(Array.get(array, i));
      }

      public String getClassName() { return "Array"; }

      public Object getDefaultValue(Class<?> typeHint) {
        if (String.class.equals(typeHint)) { return toString(); }
        return this;
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
          if (i != 0) { sb.append(','); }
          sb.append(Array.get(array, i));
        }
        return sb.toString();
      }

      public Object[] getIds() {
        Object[] indices = new Object[n + 1];
        for (int i = n; --i >= 0;) { indices[i] = i; }
        indices[n] = "length";
        return indices;
      }

      public Scriptable getParentScope() {
        return scope;
      }

      public Scriptable getPrototype() {
        return ScriptableObject.getArrayPrototype(scope);
      }

      public boolean has(String k, Scriptable s) {
        return "length".equals(k) || has(index(k), s);
      }

      public boolean has(int i, Scriptable s) {
        return i >= 0 && i < n;
      }

      public boolean hasInstance(Scriptable s) {
        throw new Error("IMPLEMENT ME");
      }

      public void put(String k, Scriptable s, Object value) {
        put(index(k), s, value);
      }

      public void put(int i, Scriptable s, Object value) {
        try {
          Array.set(array, i, fromJs(value));
        } catch (ArrayStoreException ex) {
          return;  // assignment fails silently in JS
        }
      }

      public void setParentScope(Scriptable s) {
        throw new UnsupportedOperationException();
      }

      public void setPrototype(Scriptable s) {
        throw new UnsupportedOperationException();
      }
    });
  }

  private Scriptable makeListWrapper(
      final List<?> list, @Nullable final List<Object> writeFacet) {
    return wrap(list, new Scriptable() {
      private int index(String k) {
        try {
          return Integer.parseInt(k, 10);
        } catch (NumberFormatException ex) {
          return -1;
        }
      }

      public void delete(String k) {
        delete(index(k));
      }

      public void delete(int i) {
        if (writeFacet != null) {
          // delete fails silently in JS
          if (i < 0 || i >= writeFacet.size()) { return; }
          writeFacet.remove(i);
        }
      }

      public Object get(String k, Scriptable s) {
        if ("length".equals(k)) { return list.size(); }
        int index = index(k);
        return index >= 0 ? get(index, s) : getPrototype().get(k, s);
      }

      public Object get(int i, Scriptable s) {
        if (i < 0 || i >= list.size()) { return Undefined.instance; }
        return toJs(list.get(i));
      }

      public String getClassName() { return "Array"; }

      public Object getDefaultValue(Class<?> typeHint) {
        if (String.class.equals(typeHint)) { return toString(); }
        return this;
      }

      public Object[] getIds() {
        int n = list.size();
        Object[] indices = new Object[n + 1];
        for (int i = n; --i >= 0;) { indices[i] = i; }
        indices[n] = "length";
        return indices;
      }

      public Scriptable getParentScope() {
        return scope;
      }

      public Scriptable getPrototype() {
        return ScriptableObject.getArrayPrototype(scope);
      }

      public boolean has(String k, Scriptable s) {
        return "length".equals(k) || has(index(k), s);
      }

      public boolean has(int i, Scriptable s) {
        return i >= 0 && i < list.size();
      }

      public boolean hasInstance(Scriptable s) {
        throw new Error("IMPLEMENT ME");
      }

      public void put(String k, Scriptable s, Object value) {
        if (writeFacet == null) { return; }
        if ("length".equals(k)) {
          if (!(value instanceof Number)) { return; }
          int newLen = ((Number) value).intValue();
          if (newLen < 0) { return; }
          int len = writeFacet.size();
          if (newLen < len) {
            writeFacet.subList(newLen, len).clear();
          } else {
            while (newLen > len) {
              writeFacet.add(null);
              ++len;
            }
          }
        } else {
          put(index(k), s, value);
        }
      }

      public void put(int i, Scriptable s, Object value) {
        if (writeFacet == null) { return; }
        int n = writeFacet.size();
        if (i < n) {
          writeFacet.set(i, fromJs(value));
        } else {
          while (n < i) {
            writeFacet.add(null);
            ++n;
          }
          writeFacet.add(fromJs(value));
        }
      }

      public void setParentScope(Scriptable s) {
        throw new UnsupportedOperationException();
      }

      public void setPrototype(Scriptable s) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String toString() {
        return Joiner.on(',').useForNull("null").join(list);
      }
    });
  }

  private Scriptable makeObjWrapper(
      final Map<?, ?> map, @Nullable final Map<String, Object> writeFacet) {
    return wrap(map, new Scriptable() {
      public void delete(String k) {
        if (k != null && writeFacet != null) { writeFacet.remove(k); }
      }

      public void delete(int i) { delete("" + i); }

      public Object get(String k, Scriptable s) {
        if (k == null) { return Undefined.instance; }
        Object result = toJs(map.get(k));
        return result != null || map.containsKey(k)
            ? result : getPrototype().get(k, s);
      }

      public Object get(int i, Scriptable s) { return get("" + i, s); }

      public String getClassName() { return "Object"; }

      public Object getDefaultValue(Class<?> typeHint) {
        if (String.class.equals(typeHint)) { return toString(); }
        return this;
      }

      public Object[] getIds() {
        List<Object> keys = Lists.newArrayList();
        for (Object o : map.keySet()) {
          if (o instanceof String) { keys.add(o); }
        }
        return keys.toArray();
      }

      public Scriptable getParentScope() { return scope; }

      public Scriptable getPrototype() {
        return ScriptableObject.getObjectPrototype(scope);
      }

      public boolean has(String k, Scriptable s) {
        return k != null && map.containsKey(k);
      }

      public boolean has(int i, Scriptable s) {
        return has("" + i, s);
      }

      public boolean hasInstance(Scriptable s) {
        throw new Error("IMPLEMENT ME");
      }

      public void put(String k, Scriptable s, Object value) {
        if (writeFacet != null && k != null) {
          writeFacet.put(k, fromJs(value));
        }
      }

      public void put(int i, Scriptable s, Object value) {
        put("" + i, s, value);
      }

      public void setParentScope(Scriptable s) {
        throw new UnsupportedOperationException();
      }

      public void setPrototype(Scriptable s) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String toString() {
        return "[object Object]";
      }
    });
  }

  private BaseFunction makeFunctionWrapper(MembranableFunction fn) {
    return wrap(fn, new WrappedFunction(this, fn));
  }

  private <T> T wrap(Object original, T wrapper) {
    membrane.put(original, wrapper);
    membrane.put(wrapper, original);
    return wrapper;
  }
}
