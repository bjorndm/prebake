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

package org.prebake.service.tools;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.caja.util.Strings;

/**
 * A virtual "process" that can be used to implement trivial command line tools
 * in the VM.
 *
 * <p>
 * {@code InVmProcess}es are part of the TCB so implementers are responsible for
 * maintaining the following invariants:</p><ul>
 *   <li>Do not muck with any files in the client root.
 *   TODO: Maybe pass a signal to relax this in the case where the success of
 *   this process completely determines the success of the product.
 *   <li>Do not cause any side effects after the run method completes whether
 *     normally or abnormally.  Specifically, the run method might be
 *     interrupted at any time by a
 *     {@link java.util.concurrent.CancellationException},
 *     {@link InterruptedException} or due to resource exhaustion.
 *   <li>Do not access state owned by other threads.
 *   <li>Do not access the file system except through the file system object
 *   reachable from the {@code cwd} parameter to {@link InVmProcess#run}.
 *   <li>Release all resources acquired.
 * </ul>
 */
public interface InVmProcess {
  byte run(Path workingDir, String... argv) throws IOException;

  public static final class Lookup {
    private Lookup() { /* uninstantiable */ }
    /**
     * Looks up a process handler for the given process name.
     * If the input starts with {@code "$$"} then the remainder is treated as
     * a class name prefix for a {@code *Process} class in this package.  E.g.,
     * <ul>
     *   <li><tt>$$cp</tt> => <tt>org.prebake.service.tools.ext.CpProcess</tt>
     *   <li><tt>$$jar</tt> => <tt>org.prebake.service.tools.ext.JarProcess</tt>
     * </ul>
     *
     * @return null if cmd is not handled by an in-VM process.
     */
    public static @Nullable InVmProcess forCommand(String cmd) {
      if (cmd.length() < 3 || !cmd.startsWith("$$")) { return null; }
      String className = Strings.toUpperCase(cmd.substring(2, 3))
          + cmd.substring(3) + "Process";
      String fullName = Lookup.class.getPackage().getName() + "." + className;
      try {
        ClassLoader cl = Lookup.class.getClassLoader();
        if (cl == null) { cl = ClassLoader.getSystemClassLoader(); }
        Class<?> clazz = Class.forName(fullName, false, cl);
        if (clazz != null && InVmProcess.class.isAssignableFrom(clazz)) {
          return (clazz.asSubclass(InVmProcess.class)).newInstance();
        }
        return null;
      } catch (ClassNotFoundException ex) {
        return null;
      } catch (IllegalAccessException ex) {
        return null;
      } catch (InstantiationException ex) {
        return null;
      }
    }
  }
}
