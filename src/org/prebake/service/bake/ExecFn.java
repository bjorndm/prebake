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
import org.prebake.os.OperatingSystem;
import org.prebake.os.OsProcess;

import java.io.IOException;
import java.nio.file.Path;
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
        + " or writeTo(); then run(); then waitFor() or kill().",
        "exec", "cmd", "argv...");
    this.os = os;
    this.workingDir = workingDir;
    this.checker = checker;
    this.logger = logger;
  }

  private static final Map<Object, OsProcess> JS_OBJ_TO_PROCESS = new MapMaker()
      .weakKeys().makeMap();

  public Object apply(Object[] args) {
    if (args.length == 0 || args[0] == null) {
      throw new IllegalArgumentException("No command specified");
    }
    String cmd = (String) args[0];
    List<String> argv = Lists.newArrayList();
    for (int i = 1, n = args.length; i < n; ++i) {
      if (args[i] != null) {
        try {
          argv.add(checker.check((String) args[i]));
        } catch (IllegalArgumentException ex) {
          logger.log(
              Level.WARNING, "Possible attempt to touch client dir", ex);
          throw ex;
        }
      }
    }
    final OsProcess p = os.run(
        workingDir, cmd, argv.toArray(new String[argv.size()]));
    return new JsObjMaker(p).jsObj;
  }

  final class JsObjMaker {
    final ImmutableMap<String, ?> jsObj;
    JsObjMaker(final OsProcess p) {
      this.jsObj = ImmutableMap.<String, Object>builder()
          .put("pipeTo", new SimpleMembranableFunction(
              "Links this process's output to the given process's input",
              "pipeTo", "this", "process") {
            public Object apply(Object[] args) {
              if (args.length != 1) { throw new IndexOutOfBoundsException(); }
              OsProcess q = JS_OBJ_TO_PROCESS.get(args[0]);
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
              return jsObj;
            }
          })
          .put("readFrom", new SimpleMembranableFunction(
              "Streams the given file to this process's input.",
              "readFrom", "this", "file") {
            public Object apply(Object[] args) {
              if (args.length != 1) { throw new IndexOutOfBoundsException(); }
              if (!(args[0] instanceof String)) {
                throw new ClassCastException(args[0].getClass().getName());
              }
              try {
                p.readFrom(checker.check(workingDir.resolve((String) args[0])));
              } catch (IOException ex) {
                logger.log(
                    Level.WARNING, "Possible attempt to touch client dir", ex);
                throw new RuntimeException(ex.getMessage());
              }
              return jsObj;
            }
          })
          .put("writeTo", new SimpleMembranableFunction(
              "Streams the given file to this process's input.",
              "readFrom", "this", "file") {
            public ImmutableMap<String, ?> apply(Object[] args) {
              if (args.length != 1) { throw new IndexOutOfBoundsException(); }
              if (!(args[0] instanceof String)) {
                throw new ClassCastException(args[0].getClass().getName());
              }
              try {
                Path outPath = workingDir.resolve((String) args[0]);
                // We use 0700 since we're only operating in the working dir.
                Baker.mkdirs(outPath.getParent(), 0700);
                p.writeTo(checker.check(outPath));
              } catch (IOException ex) {
                logger.log(
                    Level.WARNING, "Possible attempt to touch client dir", ex);
                throw new RuntimeException(ex.getMessage());
              }
              return jsObj;
            }
          })
          .put("run", new SimpleMembranableFunction(
              "Starts an external process.", "run", "this") {
            public Object apply(Object[] args) {
              try {
                p.run();
                runningProcesses.add(p);
              } catch (InterruptedException ex) {
                Throwables.propagate(ex);
              } catch (IOException ex) {
                Throwables.propagate(ex);
              }
              return jsObj;
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
                return p.waitFor();
              } catch (InterruptedException ex) {
                Throwables.propagate(ex);
                return -1;  // unreachable
              }
            }
          })
          .build();
      JS_OBJ_TO_PROCESS.put(jsObj, p);
    }
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
