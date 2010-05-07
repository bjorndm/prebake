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

package org.prebake.channel;

import org.prebake.service.Prebakery;

/**
 * Constants for the names of files created by the {@link Prebakery} service.
 *
 * @see <a href="http://code.google.com/p/prebake/wiki/PrebakeDirectory">wiki
 *     </a>
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class FileNames {
  public static final String DIR = ".prebake";
  public static final String CMD_LINE = "cmdline";
  public static final String PORT = "port";
  public static final String TOKEN = "token";
  public static final String ARCHIVE = "archive";

  private FileNames() { /* not instantiable */ }
}
