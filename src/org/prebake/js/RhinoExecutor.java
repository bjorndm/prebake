package org.prebake.js;

import org.prebake.core.Documentation;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrappedException;

/**
 * Do not instantiate directly.  Use {@link Executor.Factory} instead.
 * This will be obsoleted once a JDK ships with built-in scripting language
 * support and proper sand-boxing.
 *
 * @author mikesamuel@gmail.com
 */
@ParametersAreNonnullByDefault
public final class RhinoExecutor implements Executor {

  public RhinoExecutor() { /* no-op */ }

  static final Set<String> OBJECT_CLASS_MEMBERS = ImmutableSet.of(
      // We allow toString since that is part of JS as well, typically has
      // no side effect, and returns a JS primitive type.
      "class", "clone", "equals", "finalize", "getClass", "hashCode",
      "notify", "notifyAll", "wait");

  private static final Set<String> CLASS_WHITELIST = ImmutableSet.of(
      Boolean.class.getName(),
      Character.class.getName(),
      Double.class.getName(),
      // TODO: does exceptions expose non-determinism via stack details?
      EcmaError.class.getName(),
      EvaluatorException.class.getName(),
      Float.class.getName(),
      Integer.class.getName(),
      JavaScriptException.class.getName(),
      Long.class.getName(),
      RhinoException.class.getName(),
      Short.class.getName(),
      String.class.getName(),
      URI.class.getName(),
      WrappedException.class.getName(),
      // Provided extensions.
      Console.class.getName(),
      NonDeterminismRecorder.class.getName(),
      LoadFn.class.getName()
      );

  private static final ContextFactory SANDBOXINGFACTORY = new ContextFactory() {
    @Override
    protected Context makeContext() {
      // Implement Rhino sandboxing as explained at
      //     http://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
      // plus a few extra checks.
      CpuQuotaContext context = new CpuQuotaContext(this);
      // Make Rhino runtime call observeInstructionCount every so often to check
      // the CPU quota.
      context.setInstructionObserverThreshold(10000);  // TODO: tuning param
      context.setClassShutter(new ClassShutter() {
        public boolean visibleToScripts(String fullClassName) {
          if (fullClassName.endsWith("SandBoxSafe")) { return true; }
          if (CLASS_WHITELIST.contains(fullClassName)) { return true; }
          for (int dot = fullClassName.length();
               (dot = fullClassName.lastIndexOf('.', dot - 1)) >= 0;) {
            if (CLASS_WHITELIST.contains(
                    fullClassName.substring(0, dot + 1) + "*")) {
              return true;
            }
          }
          if (fullClassName.matches("[A-Z]")) {  // is a class, not a package
            System.err.println(
                "RhinoExecutor denied access to " + fullClassName);
          }
          return false;
        }
      });
      context.setWrapFactory(new SandboxingWrapFactory());
      return context;
    }
    @Override
    public boolean hasFeature(Context c, int feature) {
      switch (feature) {
        case Context.FEATURE_LOCATION_INFORMATION_IN_ERROR: return true;
        case Context.FEATURE_E4X: return false;
        case Context.FEATURE_ENHANCED_JAVA_ACCESS: return false;
        case Context.FEATURE_PARENT_PROTO_PROPERTIES: return false;
        case Context.FEATURE_STRICT_VARS: return true;
        case Context.FEATURE_STRICT_EVAL: return true;
        default: return super.hasFeature(c, feature);
      }
    }

    // The below is adapted from the ContextFactory Javadoc to time out on
    // infinite loops.
    /** Custom Context to store execution time. */
    final class CpuQuotaContext extends Context {
      CpuQuotaContext(ContextFactory f) { super(f); }
      long startTimeNanos = System.nanoTime();
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
      CpuQuotaContext qcx = (CpuQuotaContext) cx;
      long currentTime = System.nanoTime();
      if (currentTime - qcx.startTimeNanos > 5000000000L) {
        // More then 5 seconds from Context creation time:
        // it is time to stop the script.
        // Throw Error instance to ensure that script will never
        // get control back through catch or finally.
        throw new Executor.ScriptTimeoutException();
      }
    }

    @Override
    protected Object doTopCall(
        Callable callable, Context cx, Scriptable scope,
        Scriptable thisObj, Object[] args) {
      CpuQuotaContext qcx = (CpuQuotaContext) cx;
      qcx.startTimeNanos = System.nanoTime();
      return super.doTopCall(callable, cx, scope, thisObj, args);
    }
  };
  static {
    ContextFactory.initGlobal(SANDBOXINGFACTORY);
  }

  public Object toFunction(
      com.google.common.base.Function<Object[], Object> fn, String name,
      Documentation doc) {
    return new WrappedFunction.Skeleton(fn, name, doc);
  }

  /**
   * Stores either the loaded module as a {@link Function} or the exception
   * that caused module loading to fail.
   */
  private final Map<Path, Object> loadedModules = Maps.newHashMap();
  public <T> Output<T> run(
      Class<T> expectedResultType, Logger logger, @Nullable Loader loader,
      Executor.Input... srcs) {
    if (SANDBOXINGFACTORY != ContextFactory.getGlobal()) {
      throw new IllegalStateException();
    }
    Context context = SANDBOXINGFACTORY.enterContext();
    // TODO: figure out appropriate optimization level
    context.setOptimizationLevel(-1);
    try {
      return runInContext(
          srcs.clone(), context, expectedResultType, logger, loader);
    } finally {
      Context.exit();
    }
  }

  private <T> Output<T> runInContext(
      Executor.Input[] srcs, Context context, Class<T> expectedResultType,
      Logger logger, @Nullable Loader loader) {
    Runner runner = new Runner(context, logger, loader);

    Object result = null;
    AbnormalExitException exit = null;
    synchronized (context) {
      for (Input src : srcs) {
        try {
          result = runner.run(src);
        } catch (AbnormalExitException ex) {
          result = null;
          exit = ex;
          break;
        }
      }
    }
    if (result != null) {
      if (YSON.class.isAssignableFrom(expectedResultType)
          && result instanceof Scriptable) {
        Scriptable s = (Scriptable) result;
        try {
          result = toYSON(context, s);
        } catch (ParseException ex) {
          logger.log(Level.SEVERE, "Failed to convert output " + result, ex);
        }
      }
      if (!expectedResultType.isInstance(result)) {
        result = Context.jsToJava(result, expectedResultType);
      }
    }
    return new Output<T>(
        expectedResultType.cast(result), runner.nonDeterminism.used, exit);
  }

  private class Runner {
    private final Context context;
    private final Logger logger;
    private final @Nullable Loader loader;
    private final ScriptableObject globalScope;
    private final NonDeterminism nonDeterminism;
    private final Map<Input, Object> moduleResults
        = new WeakHashMap<Input, Object>();

    Runner(Context context, Logger logger, @Nullable Loader loader) {
      this.context = context;
      this.logger = logger;
      this.loader = loader;
      this.globalScope = context.initStandardObjects();
      this.nonDeterminism = new NonDeterminism();
      {
        Scriptable math = (Scriptable) ScriptableObject.getProperty(
            globalScope, "Math");
        Scriptable date = (Scriptable) ScriptableObject.getProperty(
            globalScope, "Date");
        Scriptable object = (Scriptable) ScriptableObject.getProperty(
            globalScope, "Object");
        ScriptableObject.putProperty(
            math, "random",
            new NonDeterminismRecorder(
                (Function) ScriptableObject.getProperty(math, "random"),
                Predicates.<Object[]>alwaysTrue(), nonDeterminism));
        ScriptableObject.putProperty(
            date, "now",
            new NonDeterminismRecorder(
                (Function) ScriptableObject.getProperty(date, "now"),
                Predicates.<Object[]>alwaysTrue(), nonDeterminism));
        ScriptableObject.putProperty(
            globalScope, "Date",
            new NonDeterminismRecorder(
                (Function) date,
                new Predicate<Object[]>() {
                  public boolean apply(Object[] args) {
                    return args.length == 0;
                  }
                },
                nonDeterminism));
        ScriptableObject.putProperty(object, "frozenCopy", new FrozenCopyFn());
      }
      Console console = new Console(logger);
      int constBits = ScriptableObject.DONTENUM | ScriptableObject.PERMANENT
          | ScriptableObject.READONLY;
      globalScope.defineProperty("console", console, constBits);
      globalScope.defineProperty(
          "help", new HelpFn(globalScope, console), constBits);
    }

    private Object run(Executor.Input src) throws AbnormalExitException {
      // Don't multiply evaluate an input that happens to be an actual to
      // multiple other inputs.
      if (moduleResults.containsKey(src)) { return moduleResults.get(src); }
      NativeObject actualsObj = new NativeObject();
      for (Map.Entry<String, ?> e : src.actuals.entrySet()) {
        Object value = e.getValue();
        // Inputs can be specified as inputs to other inputs.
        if (value instanceof Executor.Input) {
          value = run((Executor.Input) value);
        } else if (value instanceof ScriptableSkeleton) {
          value = ((ScriptableSkeleton) value).fleshOut(globalScope);
        }
        ScriptableObject.putConstProperty(actualsObj, e.getKey(), value);
      }
      LoadFn loadFn = new LoadFn(globalScope, loader, logger, src.base);
      if (loader != null && !ScriptableObject.hasProperty(actualsObj, "load")) {
        ScriptableObject.putConstProperty(actualsObj, "load", loadFn);
      }
      Object[] actualsObjArr = new Object[] { actualsObj };

      try {
        Object result = loadFn.load(context, src.content, src.source)
            .call(context, globalScope, globalScope, actualsObjArr);
        moduleResults.put(src, result);
        return result;
      } catch (EcmaError ex) {
        throw new AbnormalExitException(ex);
      }
    }
  }

  public final class LoadFn extends BaseFunction {
    // TODO: attach help info to load
    private final Scriptable globalScope;
    private final Loader loader;
    private final Logger logger;
    private final Path base;

    LoadFn(Scriptable globalScope, Loader loader, Logger logger, Path base) {
      this.globalScope = globalScope;
      this.loader = loader;
      this.logger = logger;
      this.base = base;
      ScriptRuntime.setFunctionProtoAndParent(this, globalScope);
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      if (Number.class.isAssignableFrom(typeHint)) { return Double.NaN; }
      if (typeHint == Boolean.class) { return Boolean.TRUE; }
      return this;
    }

    /**
     * @throws RhinoException when load fails due to IE problems, or the JS file
     *     is syntactically invalid.
     */
    @Override
    public Function call(
        Context context, Scriptable scope, Scriptable thisObj, Object[] args)
        throws RhinoException {
      Path modulePath;  // The path to load
      {
        String relPath = args[0].toString();
        String pathSep = base.getFileSystem().getSeparator();
        // URI separator should always work.
        if (!"/".equals(pathSep)) { relPath = relPath.replace("/", pathSep); }
        modulePath = base.getParent().resolve(relPath).normalize();
      }
      try {
        // The caller of load can recover from a failed load using try/catch,
        // so we want to record the dependency so that if the file comes into
        // existence, we know to invalidate the output.
        Executor.Input loaded = loader.load(modulePath);
        modulePath = loaded.base.normalize();
        String src = loaded.content;

        // See if we've already loaded it now that the loader has resolved the
        // real path.
        // Don't possibly load two versions of the same path.
        // We can't deal with version skew within a JS run.
        Object loadResult = loadedModules.get(modulePath);
        if (loadResult != null) {
          if (loadResult instanceof Function) { return (Function) loadResult; }
          Throwable th = (Throwable) loadResult;
          if (th instanceof RuntimeException) {
            throw (RuntimeException) th;
          } else {
            throw (IOException) th;
          }
        }
        LoadFn subLoadFn = new LoadFn(globalScope, loader, logger, modulePath);
        Function fn = subLoadFn.load(context, src, modulePath.toString());
        loadedModules.put(modulePath, fn);
        return fn;
      } catch (IOException ex) {
        loadedModules.put(modulePath, ex);
        throw new WrappedException(ex);
      } catch (RuntimeException ex) {
        loadedModules.put(modulePath, ex);
        throw ex;
      }
    }

    private Function load(Context context, String src, String srcName) {
      logger.log(Level.FINE, "Loading {0}", srcName);
      try {
        Script compiled;
        try {
          compiled = context.compileString(src, srcName, 1, null);
        } catch (EvaluatorException ex) {
          logger.log(Level.FINE, "JS Compilation failed {0}", src);
          throw ex;
        }
        return new Freezer().freeze(new LoadedModule(srcName, compiled));
      } finally {
        logger.log(Level.FINE, "Done    {0}", srcName);
      }
    }

    @Override
    public Scriptable construct(
        Context context, Scriptable scope, Object[] args) {
      throw new UnsupportedOperationException();
    }

    final class LoadedModule extends BaseFunction {
      // TODO: attach help info showing where it came from and
      // calling conventions.
      private final String srcName;
      private final Script body;

      LoadedModule(String srcName, Script body) {
        this.srcName = srcName;
        this.body = body;
        ScriptRuntime.setFunctionProtoAndParent(this, globalScope);
      }

      @Override public String getFunctionName() {
        int lastSlash = Math.max(
            srcName.lastIndexOf('/'), srcName.lastIndexOf('\\')) + 1;
        int dot = srcName.indexOf('.', lastSlash);
        if (dot < 0) { dot = srcName.length(); }
        if (lastSlash == dot) { return null; }
        String name = srcName.substring(0, dot);
        name = name.replace('-', '_').replaceAll("[^\\w$\\p{L}]", "");
        if (Character.isDigit(name.codePointAt(0))) { name = "_" + name; }
        return name;
      }

      @Override
      public Object getDefaultValue(Class<?> typeHint) {
        if (Number.class.isAssignableFrom(typeHint)) { return Double.NaN; }
        if (typeHint == Boolean.class) { return Boolean.TRUE; }
        return this;
      }

      @Override
      public Object call(
          Context context, Scriptable scope, Scriptable thisObj, Object[] args)
          throws RhinoException {
        ScriptableObject localScope = new LoadedModuleScope(globalScope);
        if (args.length >= 1 && args[0] instanceof Scriptable) {
          Scriptable env = (Scriptable) args[0];
          for (Object key : ScriptableObject.getPropertyIds(env)) {
            if (key instanceof Number) {
              int keyI = ((Number) key).intValue();
              ScriptableObject.putProperty(
                  localScope, keyI, ScriptableObject.getProperty(env, keyI));
            } else {
              String keyS = key.toString();
              ScriptableObject.putProperty(
                  localScope, keyS, ScriptableObject.getProperty(env, keyS));
            }
          }
        }
        if (loader != null
            && (ScriptableObject.getProperty(localScope, "load")
                instanceof LoadFn)) {
          ScriptableObject.putProperty(localScope, "load", LoadFn.this);
        }
        return body.exec(context, localScope);
      }

      @Override
      public Scriptable construct(Context c, Scriptable scope, Object[] args) {
        throw new UnsupportedOperationException();
      }
    }
  }

  private static class LoadedModuleScope extends ScriptableObject {
    public LoadedModuleScope(Scriptable globalScope) {
      super(globalScope, null);
      // We set the prototype instead of the parent scope so that
      // this.foo is properly aliased to foo.
      setPrototype(globalScope);
    }
    @Override
    public String getClassName() { return null; }
  }

  private static final class NonDeterminism {
    boolean used;
  }

  private static final class NonDeterminismRecorder extends FunctionWrapper {
    final Predicate<Object[]> argPredicate;
    final NonDeterminism nonDeterminism;

    NonDeterminismRecorder(
        Function fn, Predicate<Object[]> argPredicate,
        NonDeterminism nonDeterminism) {
      super(fn);
      this.argPredicate = argPredicate;
      this.nonDeterminism = nonDeterminism;
    }

    @Override
    public Object call(
        Context arg0, Scriptable arg1, Scriptable arg2, Object[] args) {
      if (argPredicate.apply(args)) { nonDeterminism.used = true; }
      return super.call(arg0, arg1, arg2, args);
    }
    @Override
    public Scriptable construct(Context arg0, Scriptable arg1, Object[] args) {
      if (argPredicate.apply(args)) { nonDeterminism.used = true; }
      return super.construct(arg0, arg1, args);
    }
  }

  private static YSON toYSON(Context context, @Nullable Object o)
      throws ParseException {
    StringBuilder sb = new StringBuilder();
    JsonSink sink = new JsonSink(sb);
    try {
      writeYSON(context, o, sink);
    } catch (IOException ex) {
      Throwables.propagate(ex);  // Writing to StringBuilder
    }
    return YSON.parseExpr(sb.toString());
  }

  private static void writeYSON(
      Context context, @Nullable Object o, JsonSink out)
      throws IOException {
    if (o instanceof Scriptable) {
      if (o instanceof Function) {
        Function f = (Function) o;
        String src = functionSource(context, f);
        out.write(src == null || !isYSONFunction(src, f) ? "null" : src);
      } else if (o instanceof NativeArray) {
        NativeArray a = (NativeArray) o;
        out.write("[");
        for (int i = 0, n = (int) (Math.min(Integer.MAX_VALUE, a.getLength()));
             i < n; ++i) {
          if (i != 0) { out.write(","); }
          Object el = ScriptableObject.getProperty(a, i);
          writeYSON(context, el, out);
        }
        out.write("]");
      } else {
        Scriptable s = (Scriptable) o;
        boolean firstKey = true;
        out.write("{");
        for (Object key : ScriptableObject.getPropertyIds(s)) {
          Object value;
          if (key instanceof Number) {
            value = ScriptableObject.getProperty(s, ((Number) key).intValue());
          } else {
            value = ScriptableObject.getProperty(s, key.toString());
          }
          if (value instanceof Function) {
            Function f = (Function) value;
            String src = functionSource(context, f);
            if (src == null || !isYSONFunction(src, f)) { continue; }
            if (!firstKey) { out.write(","); }
            out.writeValue(key.toString());
            out.write(":");
            out.write(src);
          } else {
            if (!firstKey) { out.write(","); }
            out.writeValue(key.toString());
            out.write(":");
            writeYSON(context, value, out);
          }
          firstKey = false;
        }
        out.write("}");
      }
    } else {
      if (o instanceof Undefined) { o = null; }
      out.writeValue(o);
    }
  }

  private static String functionSource(Context context, Function f) {
    Scriptable fnProto = f.getPrototype();
    Object toSource = ScriptableObject.getProperty(fnProto, "toSource");
    if (!(toSource instanceof Function)) { return null; }
    Object source = ((Function) toSource).call(
        context, f.getParentScope(), f, new Object[0]);
    return source instanceof String ? (String) source : null;
  }

  private static boolean isYSONFunction(String source, Function f) {
    YSON yson;
    try {
      yson = YSON.parseExpr(source);
    } catch (ParseException ex) {
      return false;  // Happens for function () { [native function] }
    }
    // If any of the free names are not global, then f is not a YSON function.
    for (String freeName : yson.getFreeNames()) {
      boolean found = false;
      boolean sawModuleScope = false;
      for (Scriptable scope = f; (scope = scope.getParentScope()) != null;) {
        if (scope instanceof LoadedModuleScope) { sawModuleScope = true; }
        if (scope.has(freeName, scope)) {  // hasOwnProperty check
          if (!sawModuleScope) { return false; }
          found = true;
          break;
        }
      }
      if (!found) { return false; }
    }
    return true;
  }
}
