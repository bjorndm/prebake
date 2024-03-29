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
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
@ParametersAreNonnullByDefault
public interface Executor {
  /**
   * Execute in the context of the given bindings and coerce the result to the
   * given type.
   * @return a result with exit set to an {@link AbnormalExitException} if the
   *     script could not produce a result.
   * @throws ClassCastException if the result could not be coerced to the
   *    expectedReturnType.
   */
  public <T> Output<T> run(
      Class<T> expectedResultType, Logger logger,
      @Nullable Loader loader, Input... input);

  /**
   * Exposed via {@link Output#exit} when JavaScript raises an exception.
   */
  public static class AbnormalExitException extends Exception {
    public AbnormalExitException(String message) { super(message); }
    public AbnormalExitException(Throwable cause) { super(cause); }
    public AbnormalExitException(String message, Throwable cause) {
      super(message, cause);
    }

    /**
     * Returns the JavaScript stack trace.
     */
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

  /**
   * Raised by {@link Executor#run} when a script runs for too long.
   */
  public static class ScriptTimeoutException extends RuntimeException {
    public ScriptTimeoutException() { super(""); }
  }

  /** Creates instances of {@link Executor}. */
  public static final class Factory {
    /** Creates a JavaScript executor. */
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
    /** The module source code. */
    public final String content;
    /** A string identifying the input which will show up in logs and stacks. */
    public final String source;
    /**
     * File against which {@link Loader loader} module paths are resolved.
     * Null indicates module cannot load.
     */
    public final Path base;
    /**
     * The globals available to the module.
     * Values may be any object that can be manipulated by the script.
     * @see MembranableFunction
     * @see MembranableList
     * @see MembranableMap
     */
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
      private Map<String, Object> actuals;

      /**
       * @param content the JavaScript program.
       * @param source the location from which the program came.  Used in
       *     stack traces.
       */
      private Builder(String content, String source) {
        assert content != null;
        assert source != null;
        this.content = content;
        this.source = source;
      }

      /**
       * Specifies the location relative to which the {@link Loader} resolves
       * loaded paths.  If null, the module will not have any loader.
       */
      public Builder withBase(@Nullable Path base) {
        this.base = base;
        return this;
      }

      /**
       * Specifies the globals available to the module.
       * Values may be any object that can be manipulated by the script.
       * @see MembranableFunction
       * @see MembranableList
       * @see MembranableMap
       */
      public Builder withActuals(Map<String, ?> actuals) {
        if (this.actuals == null) {
          this.actuals = Maps.newLinkedHashMap();
        }
        this.actuals.putAll(actuals);
        return this;
      }

      /**
       * @see #withActuals
       */
      public Builder withActual(String name, @Nullable Object value) {
        if (this.actuals == null) {
          this.actuals = Maps.newLinkedHashMap();
        }
        this.actuals.put(name, value);
        return this;
      }

      public Input build() {
        Map<String, Object> actuals;
        if (this.actuals == null) {
          actuals = ImmutableMap.of();
        } else {
          actuals = Collections.unmodifiableMap(this.actuals);
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
    /**
     * The result of the script execution or null if the script did not
     * execute normally.
     */
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
