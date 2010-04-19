package org.prebake.js;

import org.prebake.core.Documentation;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstracts away execution of script.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public interface Executor {
  /**
   * Execute in the context of the given bindings and coerce the result to the
   * given type.
   * @throws AbnormalExitException if the script could not produce a result.
   * @throws ClassCastException if the result could not be coerced to the
   *    expectedReturnType.
   */
  public <T> Output<T> run(
      Class<T> expectedResultType, Logger logger,
      @Nullable Loader loader, Input... input);

  /** Converts to a function object suitable for use in an actuals map. */
  public Object toFunction(
      Function<Object[], Object> fn, String name, Documentation doc);

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

  public static final class Factory {
    public static Executor createJsExecutor() {
      Executor executor = null;
      try {
        Class<? extends Executor> execClass = Class.forName(System.getProperty(
            "org.prebake.Executor.JS.class", "org.prebake.js.RhinoExecutor"))
            .asSubclass(Executor.class);
        executor = execClass.getConstructor().newInstance();
      } catch (InvocationTargetException ex) {
        Throwables.propagate(ex);
      } catch (ClassNotFoundException ex) {
        Throwables.propagate(ex);
      } catch (IllegalAccessException ex) {
        Throwables.propagate(ex);
      } catch (InstantiationException ex) {
        Throwables.propagate(ex);
      } catch (NoSuchMethodException ex) {
        Throwables.propagate(ex);
      }
      return executor;
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
        String content, String source, @Nullable Path base,
        Map<String, ?> actuals) {
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

      public Builder withBase(@Nullable Path base) {
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
      try {
        return new Builder(CharStreams.toString(content), source);
      } finally {
        content.close();
      }
    }

    public static Builder builder(Reader content, @Nonnull Path base)
        throws IOException {
      return builder(content, base.toString()).withBase(base);
    }

    public static Builder builder(String content, String source) {
      return new Builder(content, source);
    }

    public static Builder builder(String content, @Nonnull Path base) {
      return new Builder(content, base.toString()).withBase(base);
    }

    @Override
    public String toString() { return "(Input " + source + ")"; }
  }

  /** The results of script execution. */
  public static final class Output<T> {
    @Nullable public final T result;
    public final boolean usedSourceOfKnownNondeterminism;
    /** Non-null if the script exited abnormally. */
    @Nullable public final AbnormalExitException exit;

    public Output(
        @Nullable T result, boolean usedSourceOfKnownNondeterminism,
        @Nullable AbnormalExitException exit) {
      this.result = result;
      this.usedSourceOfKnownNondeterminism = usedSourceOfKnownNondeterminism;
      this.exit = exit;
    }
  }
}
