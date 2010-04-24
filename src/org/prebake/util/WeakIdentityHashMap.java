package org.prebake.util;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;

/**
 * Implements a combination of {@link java.util.WeakHashMap} and
 * {@link java.util.IdentityHashMap}.
 * Useful for caches that need to key off a {@code ==} comparison
 * instead of {@link Object#equals}.
 *
 * <p><b>
 * This class is not a general-purpose Map implementation! While
 * this class implements the Map interface, it intentionally violates
 * Map's general contract, which mandates the use of the equals method
 * when comparing objects. This class is designed for use only in the
 * rare cases wherein reference-equality semantics are required.</b>
 * <p>
 * <b>Note that this implementation is not synchronized.</b>
 */
public class WeakIdentityHashMap<K, V> implements Map<K, V> {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
    private final Map<IdentityWeakReference<K>, V> backingStore
        = new LinkedHashMap<IdentityWeakReference<K>, V>();
    private final Class<K> keyType;

    public WeakIdentityHashMap(Class<K> keyType) {
      this.keyType = keyType;
    }

    public void clear() {
        backingStore.clear();
        reap();
    }

    public boolean containsKey(@Nullable Object key) {
        reap();
        if (key != null && !keyType.isInstance(key)) { return false; }
        return backingStore.containsKey(
            new IdentityWeakReference<K>(keyType.cast(key), queue));
    }

    public boolean containsValue(@Nullable Object value)  {
        reap();
        return backingStore.containsValue(value);
    }

    public Set<Map.Entry<K, V>> entrySet() {
        reap();
        Set<Map.Entry<K, V>> ret = new LinkedHashSet<Map.Entry<K, V>>();
        for (Map.Entry<IdentityWeakReference<K>, V> ref
             : backingStore.entrySet()) {
            final K key = ref.getKey().get();
            final V value = ref.getValue();
            Map.Entry<K, V> entry = new Map.Entry<K, V>() {
                public K getKey() {
                    return key;
                }
                public V getValue() {
                    return value;
                }
                public V setValue(V value) {
                    throw new UnsupportedOperationException();
                }
            };
            ret.add(entry);
        }
        return Collections.unmodifiableSet(ret);
    }
    public Set<K> keySet() {
        reap();
        Set<K> ret = Sets.newLinkedHashSet();
        for (IdentityWeakReference<K> ref : backingStore.keySet()) {
            ret.add(ref.get());
        }
        return Collections.unmodifiableSet(ret);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o != null && o.getClass() == getClass()
            && backingStore.equals(
                ((WeakIdentityHashMap<?, ?>) o).backingStore);
    }

    public V get(@Nullable Object key) {
        reap();
        if (key != null && !keyType.isInstance(key)) { return null; }
        return backingStore.get(
            new IdentityWeakReference<K>(keyType.cast(key), queue));
    }
    public V put(K key, V value) {
        reap();
        return backingStore.put(
            new IdentityWeakReference<K>(key, queue), value);
    }

    @Override
    public int hashCode() {
        reap();
        return backingStore.hashCode();
    }
    public boolean isEmpty() {
        reap();
        return backingStore.isEmpty();
    }
    public void putAll(Map<? extends K, ? extends V> t) {
        throw new UnsupportedOperationException();
    }
    public V remove(@Nullable Object key) {
        reap();
        if (key != null && !keyType.isInstance(key)) { return null; }
        return backingStore.remove(new IdentityWeakReference<K>(
            keyType.cast(key), queue));
    }
    public int size() {
        reap();
        return backingStore.size();
    }
    public Collection<V> values() {
        reap();
        return backingStore.values();
    }

    private synchronized void reap() {
        Object zombie = queue.poll();

        while (zombie != null) {
            IdentityWeakReference<?> victim = (IdentityWeakReference<?>) zombie;
            backingStore.remove(victim);
            zombie = queue.poll();
        }
    }

    static final class IdentityWeakReference<K> extends WeakReference<K> {
        final int hash;

        IdentityWeakReference(@Nullable K key, ReferenceQueue<Object> queue) {
            super(key, queue);
            hash = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != IdentityWeakReference.class) {
              return false;
            }
            IdentityWeakReference<?> ref = (IdentityWeakReference<?>) o;
            if (this.get() == ref.get()) {
                return true;
            }
            return false;
        }
    }
}
