// Copyright 2010, Mike Samuel
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.prebake.js;

import org.prebake.core.DidYouMean;
import org.prebake.core.MessageQueue;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A schema for converting and checking the output of script execution.
 * These schemas are meant to be run against the output of
 * {@link YSON#toJavaObject()}.
 *
 * @param <T> the type to which this converter converts.
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface YSONConverter<T> {
  /**
   * Converts the given value to the output type, reporting an
   * {@link MessageQueue#hasErrors() error} and returning null if unable.
   * @param problems receives error messages.
   */
  @Nullable T convert(@Nullable Object ysonValue, MessageQueue problems);
  /** Describes the structure the converter expects for error messages. */
  @Nonnull String exampleText();

  /** A factory for common {@link YSONConverter converter} types. */
  public static class Factory {
    /**
     * Converts literals to the given type.
     * If the given type has a static {@code T valueOf(String)} or
     * {@code T fromString(String)} method, then it will be called to convert
     * string literals to T.
     * @param <T> the type the output converts to.
     */
    public static <T> YSONConverter<T> withType(final Class<T> type) {
      final Function<String, T> fromString;
      Method strConverter;
      try {
        strConverter = type.getDeclaredMethod(
            "valueOf", new Class[] { String.class });
      } catch (NoSuchMethodException ex) {
        try {
          strConverter = type.getDeclaredMethod(
              "fromString", new Class[] { String.class });
        } catch (NoSuchMethodException ex2) {
          strConverter = null;
        }
      }
      if (strConverter != null) {  // Works for enums and concrete number types.
        int mods = strConverter.getModifiers();
        if (Modifier.isStatic(mods) && Modifier.isPublic(mods)
            && !Modifier.isAbstract(mods)) {
          final Method convMethod = strConverter;
          fromString = new Function<String, T>() {
            public T apply(String s) {
              try {
                T t = type.cast(convMethod.invoke(null, s));
                if (t != null) { return t; }
              } catch (ClassCastException ex) {
                // handle below
              } catch (InvocationTargetException ex) {
                // handle below
              } catch (IllegalAccessException ex) {
                // handle below
              }
              return null;
            }
          };
        } else {
          fromString = null;
        }
      } else {
        fromString = null;
      }
      return new YSONConverter<T>() {
        public @Nullable T convert(
            @Nullable Object ysonValue, MessageQueue problems) {
          if (type.isInstance(ysonValue)) { return type.cast(ysonValue); }
          if (ysonValue instanceof String && fromString != null) {
            T result = fromString.apply((String) ysonValue);
            if (result != null) { return result; }
          }
          problems.error(
              "Expected an instance of " + type.getSimpleName()
              + " but was " + toErrorString(ysonValue));
          return null;
        }
        public String exampleText() {
          return "<" + type.getSimpleName().toLowerCase(Locale.ENGLISH) + ">";
        }
      };
    }

    /**
     * Yields a converter that converts {@code null} to {@code null} but
     * delegates all other values to the given converter.
     */
    public static <T> YSONConverter<T> optional(final YSONConverter<T> conv) {
      return withDefault(conv, null);
    }

    /**
     * Yields a converter that converts {@code null} to the given default value,
     * but delegates all other values to the given converter.
     */
    public static <T> YSONConverter<T> withDefault(
        final YSONConverter<? extends T> conv, @Nullable final T def) {
      return new YSONConverter<T>() {
        public @Nullable T convert(
            @Nullable Object ysonValue, MessageQueue problems) {
          return ysonValue == null ? def : conv.convert(ysonValue, problems);
        }
        public String exampleText() { return conv.exampleText(); }
      };
    }

    /**
     * Yields a converter that converts a homogeneous JavaScript object into
     * a homogeneous map.
     */
    public static <K, V> YSONConverter<Map<K, V>> mapConverter(
        final YSONConverter<K> keyConv, final YSONConverter<V> valueConv) {
      return new YSONConverter<Map<K, V>>() {
        public @Nullable Map<K, V> convert(
            @Nullable Object ysonValue, MessageQueue problems) {
          if (!(ysonValue instanceof Map<?, ?>)) {
            problems.error(
                "Expected " + exampleText() + " but got "
                + toErrorString(ysonValue));
            return null;
          }
          Map<K, V> out = Maps.newLinkedHashMap();
          for (Map.Entry<?, ?> e : ((Map<?, ?>) ysonValue).entrySet()) {
            K key = keyConv.convert(e.getKey(), problems);
            V value = valueConv.convert(e.getValue(), problems);
            out.put(key, value);
          }
          return out;
        }

        public String exampleText() {
          return "{" + keyConv.exampleText()
              + ":" + valueConv.exampleText() + ",...}";
        }
      };
    }

    /**
     * Yields a homogeneous list converter that works with
     * native JavaScript arrays.
     * @param elementConverter the converter for list elements.
     */
    public static <T> YSONConverter<List<T>> listConverter(
        final YSONConverter<T> elementConverter) {
      assert elementConverter != null;
      return new YSONConverter<List<T>>() {
        public @Nullable List<T> convert(
            @Nullable Object ysonValue, MessageQueue problems) {
          if (!(ysonValue instanceof Iterable<?>)) {
            problems.error(
                "Expected a list like " + exampleText() + " but was "
                + toErrorString(ysonValue));
            return null;
          }
          List<T> output = Lists.newArrayList();
          for (Object el : ((Iterable<?>) ysonValue)) {
            output.add(elementConverter.convert(el, problems));
          }
          return output;
        }

        public String exampleText() {
          return "[" + elementConverter.exampleText() + ", ...]";
        }
      };
    }

    /**
     * Yields a builder for a heterogeneous map converter.
     * @param keyType the type of the keys in the output map.
     *    Since JavaScript objects always use strings as keys, the output
     *    map has a homogeneous key type.
     */
    public static <K, V> YSONMapConverterBuilder<K, V> mapConverter(
        Class<K> keyType) {
      return new YSONMapConverterBuilder<K, V>(keyType);
    }

    /** A builder for a heterogeneous map converter. */
    public static final class YSONMapConverterBuilder<K, V> {
      private final Class<K> keyType;
      private final YSONConverter<K> keyIdentity;
      private YSONMapConverterBuilder(Class<K> keyType) {
        assert keyType != null;
        this.keyType = keyType;
        this.keyIdentity = Factory.withType(keyType);
      }

      private static final class Entry<K, V> {
        final String key;
        final YSONConverter<? extends K> keyConv;
        final YSONConverter<? extends V> valueConv;
        final boolean required;
        final @Nullable V defaultValue;

        Entry(String key, YSONConverter<? extends K> keyConv,
              YSONConverter<? extends V> valueConv,
              boolean required, @Nullable V defaultValue) {
          assert key != null;
          assert keyConv != null;
          assert valueConv != null;
          this.key = key;
          this.keyConv = keyConv;
          this.valueConv = valueConv;
          this.required = required;
          this.defaultValue = defaultValue;
        }
      }
      List<Entry<K, V>> entries = Lists.newArrayList();

      /**
       * Defines a required field.
       * @param key the name of a required property on the input object.
       * @param keyConv converts the property to the output key type.
       * @param valueConv converts the property value.
       * @return {@code this}
       */
      public YSONMapConverterBuilder<K, V> require(
          String key, YSONConverter<? extends K> keyConv,
          YSONConverter<? extends V> valueConv) {
        entries.add(new Entry<K, V>(key, keyConv, valueConv, true, null));
        return this;
      }

      /**
       * Defines a required field.
       * @param key the name of a required property on the input object.
       * @param valueConv converts the property value.
       * @return {@code this}
       */
      public YSONMapConverterBuilder<K, V> require(
          String key, YSONConverter<? extends V> valueConv) {
        return require(key, keyIdentity, valueConv);
      }

      /**
       * Defines an optional field.
       * @param key the name of a required property on the input object.
       * @param keyConv converts the property to the output key type.
       * @param valueConv converts the property value.
       * @param defaultValue used if the property is not present.
       * @return {@code this}
       */
      public YSONMapConverterBuilder<K, V> optional(
          String key, YSONConverter<? extends K> keyConv,
          YSONConverter<? extends V> valueConv, @Nullable V defaultValue) {
        entries.add(new Entry<K, V>(
            key, keyConv, valueConv, false, defaultValue));
        return this;
      }

      /**
       * Defines an optional field.
       * @param key the name of a required property on the input object.
       * @param valueConv converts the property value.
       * @param defaultValue used if the property is not present.
       * @return {@code this}
       */
      public YSONMapConverterBuilder<K, V> optional(
          String key, YSONConverter<? extends V> valueConv,
          @Nullable V defaultValue) {
        return optional(key, keyIdentity, valueConv, defaultValue);
      }

      /**
       * Constructs the converter based on the property definitions defined
       * prior.
       */
      public YSONConverter<Map<K, V>> build() {
        ImmutableSet.Builder<String> keyAggregator = ImmutableSet.builder();
        for (Entry<K, V> e : entries) { keyAggregator.add(e.key); }
        final ImmutableSet<String> allKeys = keyAggregator.build();
        final String[] allKeysArr = allKeys.toArray(new String[0]);
        return new YSONConverter<Map<K, V>>() {
          public @Nullable Map<K, V> convert(
              @Nullable Object ysonValue, MessageQueue problems) {
            if (!(ysonValue instanceof Map<?, ?>)) {
              problems.error(
                  "Expected " + exampleText() + ", not "
                  + toErrorString(ysonValue));
              return null;
            }
            Map<?, ?> input = (Map<?, ?>) ysonValue;
            Map<K, V> output = makeMap(keyType);
            for (Entry<K, V> e : entries) {
              if (input.containsKey(e.key)) {
                output.put(
                    e.keyConv.convert(e.key, problems),
                    e.valueConv.convert(input.get(e.key), problems));
              } else if (e.required) {
                problems.error(
                    "Missing key " + e.key + " in " + toErrorString(ysonValue));
              } else {
                output.put(e.keyConv.convert(e.key, problems), e.defaultValue);
              }
            }
            for (Object key : input.keySet()) {
              if (!allKeys.contains(key)) {
                String msg = "Unexpected key " + toErrorString(key);
                if (key instanceof String) {
                  DidYouMean.toMessageQueue(
                      msg, (String) key, problems, allKeysArr);
                } else {
                  problems.error(msg);
                }
              }
            }
            return output;
          }

          private String exampleText;
          public String exampleText() {
            if (exampleText == null) {
              StringBuilder sb = new StringBuilder();
              JsonSink sink = new JsonSink(sb);
              int limit = 40;
              try {
                sink.write("{");
                boolean skipped = false;
                for (Entry<?, ?> e : entries) {
                  int pos = sb.length();
                  if (pos > 1) { sink.write(","); }
                  sink.writeValue(e.key);
                  sink.write(":");
                  sink.write(e.valueConv.exampleText());
                  if (sb.length() >= limit) {
                    sb.setLength(pos);
                    skipped = true;
                    continue;
                  }
                }
                if (skipped) {
                  sink.write(sb.length() > 1 ? ",..." : "...");
                }
                sink.write("}");
              } catch (IOException ex) {
                Throwables.propagate(ex);  // sb is in-memory
              }
              exampleText = sb.toString();
            }
            return exampleText;
          }
        };
      }
    }

    @SuppressWarnings({ "unchecked", "cast" })
    private static <K, V> Map<K, V> makeMap(Class<K> keyType) {
      if (Enum.class.isAssignableFrom(keyType)) {
        return (Map<K, V>) Maps.newEnumMap(keyType.asSubclass(Enum.class));
      } else {
        return Maps.newLinkedHashMap();
      }
    }

    private static String toErrorString(Object o) {
      StringBuilder sb = new StringBuilder();
      JsonSink sink = new JsonSink(sb);
      try {
        sink.writeValue(o);
        sink.close();
      } catch (IOException ex) {
        Throwables.propagate(ex);  // sb is an in-memory buffer
      }
      if (sb.length() > 43) { sb.replace(20, sb.length() - 20, "..."); }
      return sb.toString();
    }
  }
}
