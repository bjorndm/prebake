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

package org.prebake.service.bake;

import org.prebake.js.SimpleMembranableFunction;
import org.prebake.js.SimpleMembranableMethod;
import org.prebake.os.OperatingSystem;
import org.prebake.os.OsProcess;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;

/**
 * A function exposed to JavaScript which allows it to spawn an external
 * process a la {@code execv} and do file and interprocess piping.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
final class ExecFn extends SimpleMembranableFunction {
  final OperatingSystem os;
  final Path workingDir;
  final WorkingFileChecker checker;
  final Logger logger;
  final List<OsProcess> runningProcesses = Lists.newArrayList();

  ExecFn(
      OperatingSystem os, Path workingDir, WorkingFileChecker checker,
      Logger logger) {
    super(
        ""
        + "Returns a command line process that you can pipeTo(), readFrom(),"
        + ", writeTo(), appendTo() and/or env(); then run();"
        + " then waitFor() or kill().",
        "exec", "cmd", "argv...");
    this.os = os;
    this.workingDir = workingDir;
    this.checker = checker;
    this.logger = logger;
  }

  private static final Map<Object, OsProcess> JS_OBJ_TO_PROCESS = new MapMaker()
      .weakKeys().makeMap();

  public Object apply(Object[] args) {
    Iterator<String> argvIt = JsOperatingSystemEnv.stringsIn(args).iterator();
    if (!argvIt.hasNext()) {
      throw new IllegalArgumentException("No command specified");
    }
    String cmd = argvIt.next();
    List<String> argv = Lists.newArrayList();
    while (argvIt.hasNext()) {
      try {
        argv.add(checker.check(argvIt.next()));
      } catch (IllegalArgumentException ex) {
        logger.log(
            Level.WARNING, "Possible attempt to touch client dir", ex);
        throw ex;
      }
    }
    OsProcess p = os.run(
        workingDir, cmd, argv.toArray(new String[argv.size()]));
    return makeJsProcessObj(p);
  }

  private Object makeJsProcessObj(final OsProcess p) {
    // The run() method should be a noop if called multiple times.
    final boolean[] run = new boolean[1];
    Object jsObj = ImmutableMap.<String, Object>builder()
        .put("pipeTo", new SimpleMembranableMethod(
            "Links this process's output to the given process's input",
            "pipeTo", "this", "process") {
          public Object apply(Object[] args) {
            if (args.length != 2) { throw new IndexOutOfBoundsException(); }
            OsProcess q = JS_OBJ_TO_PROCESS.get(args[1]);
            if (q == null) { throw new IllegalArgumentException(); }
            p.pipeTo(q);
            // Start it running if it is not already.
            try {
              if (q.runIfNotRunning()) {
                runningProcesses.add(q);
              }
            } catch (IOException ex) {
              Throwables.propagate(ex);
            } catch (InterruptedException ex) {
              Throwables.propagate(ex);
            }
            return args[0];
          }
        })
        .put("readFrom", new SimpleMembranableMethod(
            "Streams the given file to this process's input.",
            "readFrom", "this", "file") {
          public Object apply(Object[] args) {
            if (args.length != 2) { throw new IndexOutOfBoundsException(); }
            if (!(args[1] instanceof String)) {
              throw new ClassCastException(args[1].getClass().getName());
            }
            try {
              p.readFrom(checker.check(workingDir.resolve((String) args[1])));
            } catch (IOException ex) {
              logger.log(
                  Level.WARNING, "Possible attempt to touch client dir", ex);
              throw new RuntimeException(ex.getMessage());
            }
            return args[0];
          }
        })
        .put("appendTo", new SimpleMembranableMethod(
            "Streams this process's output to the end of the given file.",
            "appendTo", "this", "file") {
          public Object apply(Object[] args) {
            if (args.length != 2) { throw new IndexOutOfBoundsException(); }
            if (!(args[1] instanceof String)) {
              throw new ClassCastException(args[1].getClass().getName());
            }
            try {
              Path outPath = workingDir.resolve((String) args[1]);
              // We use 0700 since we're only operating in the working dir.
              Baker.mkdirs(outPath.getParent(), 0700);
              p.appendTo(checker.check(outPath));
            } catch (IOException ex) {
              logger.log(
                  Level.WARNING, "Possible attempt to touch client dir", ex);
              throw new RuntimeException(ex.getMessage());
            }
            return args[0];
          }
        })
        .put("writeTo", new SimpleMembranableMethod(
            "Streams this process's output to the given file.",
            "writeTo", "this", "file") {
          public Object apply(Object[] args) {
            if (args.length != 2) { throw new IndexOutOfBoundsException(); }
            if (!(args[1] instanceof String)) {
              throw new ClassCastException(args[1].getClass().getName());
            }
            try {
              Path outPath = workingDir.resolve((String) args[1]);
              // We use 0700 since we're only operating in the working dir.
              Baker.mkdirs(outPath.getParent(), 0700);
              p.writeTo(checker.check(outPath));
            } catch (IOException ex) {
              logger.log(
                  Level.WARNING, "Possible attempt to touch client dir", ex);
              throw new RuntimeException(ex.getMessage());
            }
            return args[0];
          }
        })
        .put("noInheritEnv", new SimpleMembranableMethod(
            "Don't inherit environment from the JVM",
            "noInheritEnv", "this") {
          public Object apply(Object[] args) {
            p.noInheritEnv();
            return args[0];
          }
        })
        .put("env", new SimpleMembranableMethod(
            "Sets environment key/value pairs.", "env", "this",
            "key", "value...") {
          public Object apply(Object[] args) {
            if (args.length == 2 && args[1] instanceof Map<?, ?>) {
              for (Map.Entry<?, ?> e : ((Map<?, ?>) args[1]).entrySet()) {
                p.env((String) e.getKey(), (String) e.getValue());
              }
            } else {
              Iterator<String> it = JsOperatingSystemEnv.stringsIn(
                  args, 1, args.length)
                  .iterator();
              while (it.hasNext()) {
                p.env(it.next(), it.next());
              }
            }
            return args[0];
          }
        })
        .put("run", new SimpleMembranableMethod(
            "Starts an external process.", "run", "this") {
          public Object apply(Object[] args) {
            if (!run[0]) {
              run[0] = true;
              try {
                p.run();
                runningProcesses.add(p);
              } catch (InterruptedException ex) {
                Throwables.propagate(ex);
              } catch (IOException ex) {
                Throwables.propagate(ex);
              }
            }
            return args[0];
          }
        })
        .put("kill", new SimpleMembranableFunction(
            "Kills a running process", "kill", null) {
          public Object apply(Object[] args) {
            p.kill();
            return null;
          }
        })
        .put("waitFor", new SimpleMembranableFunction(
            "Waits for a process to end and returns its exit status",
            "waitFor", "<int>") {
          public Object apply(Object[] args) {
            try {
              int result = p.waitFor();
              runningProcesses.remove(p);
              return result & 0xff;
            } catch (InterruptedException ex) {
              Throwables.propagate(ex);
              return 0xff;  // unreachable
            }
          }
        })
        .build();
    JS_OBJ_TO_PROCESS.put(jsObj, p);
    return jsObj;
  }

  void killOpenProcesses() {
    List<OsProcess> processes = Lists.newArrayList(runningProcesses);
    runningProcesses.clear();
    for (OsProcess p : processes) {
      if (p.kill()) {
        logger.log(
            Level.WARNING, "Aborted still running process {0}", p.getCommand());
      }
    }
  }
}
