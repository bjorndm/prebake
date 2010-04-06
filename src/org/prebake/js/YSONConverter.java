package org.prebake.js;

import org.prebake.core.DidYouMean;
import org.prebake.core.MessageQueue;

import com.google.common.base.Function;
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

/**
 * A schema for converting and checking the output of script execution.
 * These schemas are meant to be run against the output of
 * {@link YSON#toJavaObject()}.
 *
 * @param <T> the type to which this converter converts.
 * @author mikesamuel@gmail.com
 */
public interface YSONConverter<T> {
  /**
   * Converts the given value to the output type, reporting an
   * {@link MessageQueue#hasErrors() error} and returning null if unable.
   * @param problems receives error messages.
   */
  T convert(Object ysonValue, MessageQueue problems);

  String exampleText();

  public static class Factory {
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
        public T convert(Object ysonValue, MessageQueue problems) {
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

    public static <T> YSONConverter<T> optional(final YSONConverter<T> conv) {
      return withDefault(conv, null);
    }

    public static <T> YSONConverter<T> withDefault(
        final YSONConverter<? extends T> conv, final T def) {
      return new YSONConverter<T>() {
        public T convert(Object ysonValue, MessageQueue problems) {
          return ysonValue == null ? def : conv.convert(ysonValue, problems);
        }
        public String exampleText() { return conv.exampleText(); }
      };
    }

    public static <K, V> YSONMapConverterBuilder<K, V> mapConverter(
        Class<K> keyType) {
      return new YSONMapConverterBuilder<K, V>(keyType);
    }

    public static <K, V> YSONConverter<Map<K, V>> mapConverter(
        final YSONConverter<K> keyConv, final YSONConverter<V> valueConv) {
      return new YSONConverter<Map<K, V>>() {
        public Map<K, V> convert(Object ysonValue, MessageQueue problems) {
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

    public static <T> YSONConverter<List<T>> listConverter(
        final YSONConverter<T> elementConverter) {
      assert elementConverter != null;
      return new YSONConverter<List<T>>() {
        public List<T> convert(Object ysonValue, MessageQueue problems) {
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
        final V defaultValue;

        Entry(String key, YSONConverter<? extends K> keyConv,
              YSONConverter<? extends V> valueConv,
              boolean required, V defaultValue) {
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

      public YSONMapConverterBuilder<K, V> require(
          String key, YSONConverter<? extends K> keyConv,
          YSONConverter<? extends V> valueConv) {
        entries.add(new Entry<K, V>(key, keyConv, valueConv, true, null));
        return this;
      }

      public YSONMapConverterBuilder<K, V> require(
          String key, YSONConverter<? extends V> valueConv) {
        return require(key, keyIdentity, valueConv);
      }

      public YSONMapConverterBuilder<K, V> optional(
          String key, YSONConverter<? extends K> keyConv,
          YSONConverter<? extends V> valueConv, V defaultValue) {
        entries.add(new Entry<K, V>(
            key, keyConv, valueConv, false, defaultValue));
        return this;
      }

      public YSONMapConverterBuilder<K, V> optional(
          String key, YSONConverter<? extends V> valueConv, V defaultValue) {
        return optional(key, keyIdentity, valueConv, defaultValue);
      }

      public YSONConverter<Map<K, V>> build() {
        ImmutableSet.Builder<String> keyAggregator = ImmutableSet.builder();
        for (Entry<K, V> e : entries) { keyAggregator.add(e.key); }
        final ImmutableSet<String> allKeys = keyAggregator.build();
        final String[] allKeysArr = allKeys.toArray(new String[0]);
        return new YSONConverter<Map<K, V>>() {
          public Map<K, V> convert(Object ysonValue, MessageQueue problems) {
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
                throw new RuntimeException(ex);
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
        throw new RuntimeException(ex);  // sb is an in-memory buffer
      }
      if (sb.length() > 43) { sb.replace(20, sb.length() - 20, "..."); }
      return sb.toString();
    }
  }
}
