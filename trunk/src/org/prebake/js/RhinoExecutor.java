package org.prebake.js;

import org.prebake.core.Hash;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.WrappedException;

/**
 * Do not instantiate directly.  Use {@link Executor.Factory} instead.
 * This will be obsoleted once a JDK ships with built-in scripting language
 * support and proper sand-boxing.
 *
 * @author mikesamuel@gmail.com
 */
public final class RhinoExecutor implements Executor {
  private final Executor.Input[] srcs;

  public RhinoExecutor(Executor.Input[] srcs) { this.srcs = srcs.clone(); }

  private static final Set<String> OBJECT_CLASS_MEMBERS = ImmutableSet.of(
      // We allow toString since that is part of JS as well, typically has
      // no side effect, and returns a JS primitive type.
      "class", "clone", "equals", "finalize", "getClass", "hashCode",
      "notify", "notifyAll", "wait");

  private static final Set<String> CLASS_WHITELIST = ImmutableSet.of(
      Boolean.class.getName(),
      ByteArrayInputStream.class.getName(),
      Character.class.getName(),
      Double.class.getName(),
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
      Context context = super.makeContext();
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
      context.setWrapFactory(new WrapFactory() {
        @SuppressWarnings("unchecked")  // Overridden method is not generic
        @Override
        public Object wrap(
            Context cx, Scriptable scope, Object javaObject, Class staticType) {
          // Deny reflective access up front.  This should not be triggered due
          // to getter filtering, but let's be paranoid.
          if (javaObject != null
              && (javaObject instanceof Class
                  || javaObject instanceof ClassLoader
                  || "java.lang.reflect".equals(
                      javaObject.getClass().getPackage().getName()))) {
            return Context.getUndefinedValue();
          }
          // Make java arrays behave like native JS arrays.
          // This breaks EQ, but is better than the alternative.
          if (javaObject instanceof Object[]) {
            Object[] javaArray = (Object[]) javaObject;
            int n = javaArray.length;
            Object[] wrappedElements = new Object[n];
            Class<?> compType = javaArray.getClass().getComponentType();
            for (int i = n; --i >= 0;) {
              wrappedElements[i] = wrap(cx, scope, javaArray[i], compType);
            }
            NativeArray jsArray = new NativeArray(wrappedElements);
            jsArray.setPrototype(
                ScriptableObject.getClassPrototype(scope, "Array"));
            jsArray.setParentScope(scope);
            return jsArray;
          }
          return super.wrap(cx, scope, javaObject, staticType);
        }

        @SuppressWarnings("unchecked")  // Overridden method is not generic
        @Override
        public Scriptable wrapAsJavaObject(
            Context cx, Scriptable scope, Object javaObject, Class staticType) {
          return new NativeJavaObject(scope, javaObject, staticType) {
            @Override
            public Object get(String name, Scriptable start) {
              // Deny access to all members of the base Object class since
              // some of them enable reflection, and the others are mostly for
              // serialization and timing which should not be accessible.
              // The codeutopia implementation only blacklists getClass.
              if (OBJECT_CLASS_MEMBERS.contains(name)) { return NOT_FOUND; }
              return super.get(name, start);
            }
          };
        }
      });
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
  };
  static {
    ContextFactory.initGlobal(SANDBOXINGFACTORY);
  }

  private final Map<Path, Function> loadedModules = Maps.newHashMap();
  public <T> Output<T> run(Map<String, ?> actuals, Class<T> expectedResultType,
                           Logger logger, Loader loader)
      throws AbnormalExitException {
    if (SANDBOXINGFACTORY != ContextFactory.getGlobal()) {
      throw new IllegalStateException();
    }
    Context context = SANDBOXINGFACTORY.enterContext();
    // TODO: figure out appropriate optimization level
    context.setOptimizationLevel(-1);
    try {
      return runInContext(context, actuals, expectedResultType, logger, loader);
    } finally {
      Context.exit();
    }
  }

  private <T> Output<T> runInContext(
      Context context, Map<String, ?> actuals, Class<T> expectedResultType,
      Logger logger, Loader loader)
      throws AbnormalExitException {
    ScriptableObject globalScope = context.initStandardObjects();
    Map<Path, Hash> dynamicLoads = Maps.newLinkedHashMap();
    NonDeterminism nonDeterminism = new NonDeterminism();
    {
      Scriptable math = (Scriptable) ScriptableObject.getProperty(
          globalScope, "Math");
      Scriptable date = (Scriptable) ScriptableObject.getProperty(
          globalScope, "Date");
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
                public boolean apply(Object[] args) { return args.length == 0; }
              },
              nonDeterminism));
    }
    try {
      globalScope.defineProperty(
          "console", new Console(logger), ScriptableObject.DONTENUM);

      NativeObject actualsObj = new NativeObject();
      for (Map.Entry<String, ?> e : actuals.entrySet()) {
        ScriptableObject.putConstProperty(actualsObj, e.getKey(), e.getValue());
      }
      Object[] actualsObjArr = new Object[] { actualsObj };

      Object result = null;
      synchronized (context) {
        for (Input src : srcs) {
          String inputRead = drain(src.input);
          LoadFn loadFn = new LoadFn(
              globalScope, loader, logger, src.base, dynamicLoads);
          try {
            result = loadFn.load(context, inputRead, src.source)
                .call(context, globalScope, globalScope, actualsObjArr);
          } catch (EcmaError ex) {
            throw new AbnormalExitException(ex);
          }
          if (inputRead.length() > 500) { inputRead = "<ABBREVIATED>"; }
        }
        if (result == null) { return null; }
        if (String.class.equals(expectedResultType)
            && result instanceof Scriptable) {
          Scriptable s = (Scriptable) result;
          Object toSource = ScriptableObject.getProperty(s, "toSource");
          if (toSource instanceof Function) {
            result = ((Function) toSource).call(
                context, globalScope, s, new Object[0]);
          }
        }
        if (!expectedResultType.isInstance(result)) {
          result = Context.jsToJava(result, expectedResultType);
        }
      }
      return new Output<T>(
          expectedResultType.cast(result), dynamicLoads, nonDeterminism.used);
    } catch (IOException ex) {
      throw new AbnormalExitException(ex);
    }
  }

  private static final String drain(Reader r)
      throws IOException {
    try {
      char[] buf = new char[4096];
      StringBuilder sb = new StringBuilder();
      for (int n; (n = r.read(buf)) >= 0;) { sb.append(buf, 0, n); }
      return sb.toString();
    } finally {
      r.close();
    }
  }

  private static final Pattern STACK_FRAME = Pattern.compile(
      "^\tat ([^:]+):(\\d+)(?: \\(([^)]+)\\))?", Pattern.MULTILINE);

  public static class Console {
    private final Logger logger;
    private final List<Group> groups = Lists.newArrayList();
    private final Map<String, Long> timers = Maps.newHashMap();

    Console(Logger logger) {
      this.logger = logger;
    }

    private static boolean requiresFloatingPoint(char ch) {
      switch (ch) {
        case 'e': case 'E': case 'f': case 'g': case 'G': case 'a': case 'A':
          return true;
        default:
          return false;
      }
    }

    private static final Pattern FORMAT_SPECIFIER = Pattern.compile(
        "%(?:(?:\\d+\\$)?(?:[\\-#+ 0,(]+)?(?:\\d+)?(?:\\.\\d+)?([a-zA-Z])|%)");

    private void log(Level level, String format, Object... args) {
      char[] fmtChars = null;
      for (int i = args.length; --i >= 0;) {
        Object o = args[i];
        if (o instanceof Double
            && ((Double) o).doubleValue() == ((Double) o).longValue()) {
          // Convert Doubles to Longs when doing so does not lose information.
          // This solves the problem of console.log("%s", 1) logging "1.0",
          // and fixes "%d".
          if (fmtChars == null) {
            fmtChars = new char[args.length];
            Matcher m = FORMAT_SPECIFIER.matcher(format);
            int f = 0;
            while (m.find() && f < fmtChars.length) {
              String s = m.group(1);
              if (s != null) { fmtChars[f++] = s.charAt(0); }
            }
            System.err.println("fmtChars=" + Arrays.toString(fmtChars));
            if (requiresFloatingPoint(fmtChars[i])) { continue; }
            args = args.clone();
          } else if (requiresFloatingPoint(fmtChars[i])) {
            continue;
          }
          args[i] = Long.valueOf(((Double) o).longValue());
        }
      }
      StringBuilder sb = new StringBuilder();
      int nSpaces = groups.size() * 2;
      while (nSpaces >= 16) { sb.append(SIXTEEN_SPACES); nSpaces -= 16; }
      sb.append(SIXTEEN_SPACES, 0, nSpaces);
      Formatter f = new Formatter(sb /*, default Locale */);
      f.format(format, args);
      LogRecord lr = new LogRecord(level, sb.toString());
      Matcher m = STACK_FRAME.matcher(
          new EvaluatorException(null).getScriptStackTrace());
      if (m.find()) {
        String file = m.group(1);
        String line = m.group(2);
        String fnName = m.group(3);
        lr.setSourceClassName(file + ":" + line);
        lr.setSourceMethodName(fnName != null ? fnName : "<anonymous>");
      }
      logger.log(lr);
    }

    public void log(String format, Object... args) {
      log(Level.INFO, format, args);
    }

    public void warn(String format, Object... args) {
      log(Level.WARNING, format, args);
    }

    public void error(String format, Object... args) {
      log(Level.SEVERE, format, args);
    }

    public void info(String format, Object... args) {
      log(Level.FINE, format, args);
    }

    public void debug(String format, Object... args) {
      log(Level.INFO, format, args);
    }

    public void assert_(Object truth) { assert_(truth, null); }

    public void assert_(Object truth, Object message) {
      if (!Context.toBoolean(truth)) {
        if (message == null) { message = "Assertion Failure"; }
        String messageStr = message.toString();
        error(messageStr);
        throw new JavaScriptException(messageStr, "console", 1);
      }
    }

    public void dir(Object obj) {
      List<String> pairs = Lists.newArrayList("Name", "Value");
      if (obj instanceof Scriptable) {
        Scriptable s = (Scriptable) obj;
        for (Object id : s.getIds()) {
          if (id instanceof Number) {
            pairs.add("" + id);
            pairs.add(valueToStr(s.get(((Number) id).intValue(), s)));
          } else {
            String idStr = (String) id;
            pairs.add(idStr);
            pairs.add(valueToStr(s.get(idStr, s)));
          }
        }
      }
      log(Level.INFO, toTable(pairs, 2));
    }

    public void group(String name) {
      log(Level.INFO, "Enter " + name);
      groups.add(new Group(name));
    }

    public void groupEnd() {
      log(Level.INFO, "Exit  " + groups.remove(0).name);
    }

    public void time(String name) {
      timers.put(name, System.nanoTime());
    }

    public void timeEnd(String name) {
      Long t0 = timers.remove(name);
      if (t0 != null) {
        long t1 = System.nanoTime();
        log(Level.INFO, "Timer %s took %sns", name, t1 - t0);
      }
    }

    public void trace() {
      log(Level.INFO, new EvaluatorException(null).getScriptStackTrace());
    }

    // TODO: profile, profileEnd, count

    private static final String SIXTEEN_SPACES = "                ";
    static { assert 16 == SIXTEEN_SPACES.length(); }

    private static String valueToStr(Object o) {
      if (o == null) { return "null"; }
      if (o.getClass().isArray()) {
        if (o instanceof Object[]) { return Arrays.toString((Object[]) o); }
        Class<?> cl = o.getClass().getComponentType();
        if (cl == Byte.TYPE) { return Arrays.toString((byte[]) o); }
        if (cl == Character.TYPE) { return Arrays.toString((char[]) o); }
        if (cl == Double.TYPE) { return Arrays.toString((double[]) o); }
        if (cl == Float.TYPE) { return Arrays.toString((float[]) o); }
        if (cl == Integer.TYPE) { return Arrays.toString((int[]) o); }
        if (cl == Long.TYPE) { return Arrays.toString((long[]) o); }
        if (cl == Short.TYPE) { return Arrays.toString((short[]) o); }
      }
      return Context.toString(o);
    }

    private static String toTable(List<? extends String> cells, int n) {
      int m = cells.size() / n;
      int[] maxLens = new int[n];
      for (int i = 0; i < m * n; ++i) {
        maxLens[i % n] = Math.max(maxLens[i % n], cells.get(i).length());
      }
      int rowLen = 3 * n;  // n + 1 dividers, 2(n - 1) spaces, and 1 newline.
      for (int len : maxLens) { rowLen += len; }
      StringBuilder sb = new StringBuilder(rowLen * m);
      for (int k = 0; k < m * n;) {
        sb.append("\n|");
        for (int j = 0; j < n; ++j, ++k) {
          sb.append(' ');
          String s = cells.get(k);
          sb.append(s);
          int padding = maxLens[j] - s.length();
          while (padding >= 16) {
            sb.append(SIXTEEN_SPACES);
            padding -= 16;
          }
          sb.append(SIXTEEN_SPACES, 0, padding);
          sb.append(" |");
        }
      }
      return sb.toString();
    }
  }

  private static final class Group {
    final String name;

    Group(String name) { this.name = name; }
  }

  public class LoadFn extends ScriptableObject implements Function {
    private final Scriptable globalScope;
    private final Loader loader;
    private final Logger logger;
    private final Path base;
    private final Map<Path, Hash> dynamicLoads;

    LoadFn(
        Scriptable globalScope, Loader loader, Logger logger, Path base,
        Map<Path, Hash> dynamicLoads) {
      this.globalScope = globalScope;
      this.loader = loader;
      this.logger = logger;
      this.base = base;
      this.dynamicLoads = dynamicLoads;
    }

    @Override public String getTypeOf() { return "function"; }

    @Override
    public String getClassName() { return null; }

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
    public Object call(
        Context context, Scriptable scope, Scriptable thisObj, Object[] args)
        throws RhinoException {
      LoadFn subLoadFn;  // The loaded module
      String src;  // The source code.
      String srcName;  // The name that will appear in stack traces.
      Path modulePath;  // The path to load
      {
        String relPath = args[0].toString();
        String pathSep = base.getFileSystem().getSeparator();
        // URI separator should always work.
        if (!"/".equals(pathSep)) { relPath = relPath.replace("/", pathSep); }
        modulePath = base.getParent().resolve(relPath).normalize();
        Function fn = loadedModules.get(modulePath);
        if (fn != null) { return fn; }
        dynamicLoads.put(modulePath, null);
      }
      try {
        // The caller of load can recover from a failed load using try/catch,
        // so we want to record the dependency so that if the file comes into
        // existence, we know to invalidate the output.
        src = drain(loader.load(modulePath));
        dynamicLoads.put(
            modulePath, Hash.builder().withString(src).toHash());
        subLoadFn = new LoadFn(
            globalScope, loader, logger, modulePath, dynamicLoads);
        srcName = modulePath.toString();
      } catch (IOException ex) {
        throw new WrappedException(ex);
      }
      Function fn = subLoadFn.load(context, src, srcName);
      loadedModules.put(modulePath, fn);
      return fn;
    }

    private Function load(Context context, String src, String srcName) {
      logger.log(Level.FINE, "Loading {0}", srcName);
      try {
        return freeze(
            context,
            new LoadedModule(
                srcName, context.compileString(src, srcName, 1, null)));
      } finally {
        logger.log(Level.FINE, "Done    {0}", srcName);
      }
    }

    private <T extends ScriptableObject> T freeze(Context cx, T obj) {
      for (Object name : obj.getAllIds()) {
        ScriptableObject desc = new NativeObject();
        desc.put("value", obj.get(name));
        desc.put("writable", Boolean.FALSE);
        desc.put("configurable", Boolean.FALSE);
        obj.defineOwnProperty(cx, name, desc);
      }
      obj.preventExtensions();
      return obj;
    }

    public Scriptable construct(
        Context context, Scriptable scoe, Object[] args) {
      throw new UnsupportedOperationException();
    }

    final class LoadedModule extends ScriptableObject implements Function {
      private final String srcName;
      private final Script body;

      LoadedModule(String srcName, Script body) {
        this.srcName = srcName;
        this.body = body;
      }

      @Override public String getTypeOf() { return "function"; }

      @Override public String getClassName() { return srcName; }

      @Override
      public Object getDefaultValue(Class<?> typeHint) {
        if (Number.class.isAssignableFrom(typeHint)) { return Double.NaN; }
        if (typeHint == Boolean.class) { return Boolean.TRUE; }
        return this;
      }

      public Object call(
          Context context, Scriptable scope, Scriptable thisObj, Object[] args)
          throws RhinoException {
        ScriptableObject localScope = new ScriptableObject(globalScope, null) {
          @Override
          public String getClassName() { return null; }
        };
        localScope.setPrototype(globalScope);
        if (loader != null) {
          ScriptableObject.putProperty(localScope, "load", LoadFn.this);
        }
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
        return body.exec(context, localScope);
      }

      public Scriptable construct(Context c, Scriptable scope, Object[] args) {
        throw new UnsupportedOperationException();
      }
    }
  }

  private static class NonDeterminism {
    boolean used;
  }

  private static final class NonDeterminismRecorder
      implements Scriptable, Function {
    final Function fn;
    final Predicate<Object[]> argPredicate;
    final NonDeterminism nonDeterminism;

    NonDeterminismRecorder(Function fn, Predicate<Object[]> argPredicate,
                           NonDeterminism nonDeterminism) {
      this.fn = fn;
      this.argPredicate = argPredicate;
      this.nonDeterminism = nonDeterminism;
    }

    public void delete(String arg0) { fn.delete(arg0); }
    public void delete(int arg0) { fn.delete(arg0); }
    public Object get(String arg0, Scriptable arg1) {
      return fn.get(arg0, arg1);
    }
    public Object get(int arg0, Scriptable arg1) {
      return fn.get(arg0, arg1);
    }
    public String getClassName() { return fn.getClassName(); }
    public Object getDefaultValue(Class<?> arg0) {
      return fn.getDefaultValue(arg0);
    }
    public Object[] getIds() { return fn.getIds(); }
    public Scriptable getParentScope() { return fn.getParentScope(); }
    public Scriptable getPrototype() { return fn.getPrototype(); }
    public boolean has(String arg0, Scriptable arg1) {
      return fn.has(arg0, arg1);
    }
    public boolean has(int arg0, Scriptable arg1) {
      return fn.has(arg0, arg1);
    }
    public boolean hasInstance(Scriptable arg0) {
      return fn.hasInstance(arg0);
    }
    public void put(String arg0, Scriptable arg1, Object arg2) {
      fn.put(arg0, arg1, arg2);
    }
    public void put(int arg0, Scriptable arg1, Object arg2) {
      fn.put(arg0, arg1, arg2);
    }
    public void setParentScope(Scriptable arg0) {
      fn.setParentScope(arg0);
    }
    public void setPrototype(Scriptable arg0) {
      fn.setPrototype(arg0);
    }
    public Object call(
        Context arg0, Scriptable arg1, Scriptable arg2, Object[] args) {
      if (argPredicate.apply(args)) {
        nonDeterminism.used = true;
      }
      return fn.call(arg0, arg1, arg2, args);
    }
    public Scriptable construct(Context arg0, Scriptable arg1, Object[] args) {
      if (argPredicate.apply(args)) {
        nonDeterminism.used = true;
      }
      return fn.construct(arg0, arg1, args);
    }
  }
}