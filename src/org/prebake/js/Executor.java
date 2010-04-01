package org.prebake.js;

import org.prebake.core.Hash;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
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
      Map<String, ?> actuals, Class<T> expectedResultType,
      Logger logger, Loader loader)
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
    public final Reader input;
    public final String source;
    public final Path base;

    public Input(Reader input, Path base) {
      this(input, base.toString(), base);
    }

    /** @param source file path or URL from which the JavaScript came. */
    public Input(Reader input, String source, Path base) {
      this.input = input;
      this.source = source;
      this.base = base;
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
