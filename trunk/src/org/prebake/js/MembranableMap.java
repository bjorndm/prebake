package org.prebake.js;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Marker interface for a map that can pass across the JavaScript membrane to
 * be exposed as an object.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface MembranableMap extends Map<String, Object> {
  public static final class Factory {
    private Factory() { /* not instantiable */ }

    public static MembranableMap create(final Map<String, Object> map) {
      class Impl extends AbstractMap<String, Object> implements MembranableMap {
        @Override public void clear() { map.clear(); }

        @Override public boolean containsKey(Object k) {
          return map.containsKey(k);
        }

        @Override public boolean containsValue(Object v) {
          return map.containsValue(v);
        }

        @Override public Set<Map.Entry<String, Object>> entrySet() {
          return map.entrySet();
        }

        @Override public Object get(Object k) { return map.get(k); }

        @Override public boolean isEmpty() { return map.isEmpty(); }

        @Override public Set<String> keySet() { return map.keySet(); }

        @Override public Object put(String k, Object v) {
          return map.put(k, v);
        }

        @Override
        public void putAll(Map<? extends String, ? extends Object> all) {
          map.putAll(all);
        }

        @Override public Object remove(Object k) { return map.remove(k); }

        @Override public int size() { return map.size(); }

        @Override public Collection<Object> values() { return map.values(); }
      }

      return new Impl();
    }
  }
}
