package org.prebake.js;

import org.prebake.core.Hash;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Abstracts away execution of script.
 *
 * @author mikesamuel@gmail.com
 */
public interface Executor {
  /**
   * Execute in the context of the given bindings and coerce the result to the
   * given type.
   * @throws AbnormalExitException if the script could not produce a result.
   * @throws ClassCastException if the result could not be coerced to the
   *    expectedReturnType.
   */
  public <T> Output<T> run(
      Class<T> expectedResultType, Logger logger, Loader loader)
      throws AbnormalExitException;

  public static class AbnormalExitException extends Exception {
    public AbnormalExitException(String message) { super(message); }
    public AbnormalExitException(Throwable cause) { super(cause); }
    public AbnormalExitException(String message, Throwable cause) {
      super(message, cause);
    }

    public String getScriptTrace() {
      Throwable th = this;
      if (th.getCause() != null) { th = th.getCause(); }
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      th.printStackTrace(pw);
      pw.flush();
      return sw.toString();
    }
  }

  public static class ScriptTimeoutException extends RuntimeException {
    public ScriptTimeoutException() { super(""); }
  }

  public static class MalformedSourceException extends Exception {
    public MalformedSourceException(String message) { super(message); }
    public MalformedSourceException(Throwable cause) { super(cause); }
    public MalformedSourceException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class Factory {
    public static Executor createJsExecutor(Input... srcs)
        throws MalformedSourceException {
      Throwable cause;
      try {
        Class<? extends Executor> execClass = Class.forName(System.getProperty(
            "org.prebake.Executor.JS.class", "org.prebake.js.RhinoExecutor"))
            .asSubclass(Executor.class);
        return execClass.getConstructor(Input[].class)
            .newInstance((Object) srcs);
      } catch (InvocationTargetException ex) {
        throw new MalformedSourceException(ex);
      } catch (ClassNotFoundException ex) {
        cause = ex;
      } catch (IllegalAccessException ex) {
        cause = ex;
      } catch (InstantiationException ex) {
        cause = ex;
      } catch (NoSuchMethodException ex) {
        cause = ex;
      }
      throw new RuntimeException("Can't recover from bad config",
          cause);
    }

    private Factory() { /* not instantiable */ }
  }

  /** An input JavaScript file. */
  public static final class Input {
    public final String content;
    /** A string identifying the input which will show up in logs and stacks. */
    public final String source;
    /**
     * File against which {@link Loader loader} module paths are resolved.
     * Null indicates module cannot load.
     */
    public final Path base;
    public final Map<String, ?> actuals;

    private Input(
        String content, String source, Path base, Map<String, ?> actuals) {
      this.content = content;
      this.source = source;
      this.base = base;
      this.actuals = actuals;
    }

    /** Used to construct {@link Input}s.  @see Input#builder */
    public static class Builder {
      private final String content;
      private final String source;
      private Path base;
      private Map<String, ?> actuals;

      private Builder(String content, String source) {
        assert content != null;
        assert source != null;
        this.content = content;
        this.source = source;
      }

      public Builder withBase(Path base) {
        this.base = base;
        return this;
      }

      public Builder withActuals(Map<String, ?> actuals) {
        this.actuals = actuals;
        return this;
      }

      public Input build() {
        Map<String, Object> actuals;
        if (this.actuals == null) {
          actuals = ImmutableMap.of();
        } else if (!this.actuals.containsValue(null)) {
          actuals = ImmutableMap.copyOf(this.actuals);
        } else {
          actuals = Collections.unmodifiableMap(
              Maps.newLinkedHashMap(this.actuals));
        }
        return new Input(content, source, base, actuals);
      }
    }

    /** @param source file path or URL from which the JavaScript came. */
    public static Builder builder(Reader content, String source)
        throws IOException {
      StringBuilder sb = new StringBuilder();
      try {
        char[] buf = new char[4096];
        for (int n; (n = content.read(buf)) > 0;) { sb.append(buf, 0, n); }
      } finally {
        content.close();
      }
      return new Builder(sb.toString(), source);
    }

    public static Builder builder(Reader content, Path base)
        throws IOException {
      return builder(content, base.toString()).withBase(base);
    }

    public static Builder builder(String content, String source) {
      return new Builder(content, source);
    }

    public static Builder builder(String content, Path base) {
      return new Builder(content, base.toString()).withBase(base);
    }

    @Override
    public String toString() { return "(Input " + source + ")"; }
  }

  /** The results of script execution. */
  public static final class Output<T> {
    public final T result;
    public final Map<Path, Hash> dynamicLoads;
    public final boolean usedSourceOfKnownNondeterminism;

    public Output(T result, Map<Path, Hash> dynamicLoads,
                  boolean usedSourceOfKnownNondeterminism) {
      this.result = result;
      this.dynamicLoads = dynamicLoads;
      this.usedSourceOfKnownNondeterminism = usedSourceOfKnownNondeterminism;
    }
  }
}
