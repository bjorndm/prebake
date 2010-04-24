package org.prebake.js;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Marker interface for a list that can pass across the JavaScript membrane to
 * be exposed as an array-like object.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public interface MembranableList extends List<Object> {
  public static final class Factory {
    private Factory() { /* not instantiable */ }

    public static MembranableList create(final List<Object> list) {
      class Impl extends AbstractList<Object> implements MembranableList {
        @Override public Object get(int i) { return list.get(i); }
        @Override public int size() { return list.size(); }
        @Override public boolean add(Object o) { return list.add(o); }
        @Override public void add(int i, Object o) { list.add(i, o); }
        @Override public boolean addAll(Collection<? extends Object> all) {
          return list.addAll(all);
        }
        @Override public boolean addAll(
            int i, Collection<? extends Object> all) {
          return list.addAll(i, all);
        }
        @Override public void clear() { list.clear(); }
        @Override public boolean contains(Object o) { return list.contains(o); }
        @Override public boolean containsAll(Collection<?> all) {
          return list.containsAll(all);
        }
        @Override public int indexOf(Object o) { return list.indexOf(o); }
        @Override public boolean isEmpty() { return list.isEmpty(); }
        @Override public Iterator<Object> iterator() {
          return list.iterator();
        }
        @Override public int lastIndexOf(Object o) {
          return list.lastIndexOf(o);
        }
        @Override public ListIterator<Object> listIterator() {
          return list.listIterator();
        }
        @Override public ListIterator<Object> listIterator(int i) {
          return list.listIterator(i);
        }
        @Override public boolean remove(Object o) { return list.remove(o); }
        @Override public Object remove(int i) { return list.remove(i); }
        @Override public boolean removeAll(Collection<?> all) {
          return list.removeAll(all);
        }
        @Override public boolean retainAll(Collection<?> all) {
          return list.retainAll(all);
        }
        @Override public Object set(int i, Object o) { return list.set(i, o); }
        @Override public List<Object> subList(int s, int e) {
          return create(list.subList(s, e));
        }
        @Override public Object[] toArray() { return list.toArray(); }
        @Override public <T> T[] toArray(T[] arr) { return list.toArray(arr); }
      }
      return new Impl();
    }
  }
}
