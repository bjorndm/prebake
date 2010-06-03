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

/**
 * A pair of a timestamp and a ToolSignature.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class ToolBoxResult {
  /** Time when building a tool started relative to the logs clock. */
  final long t0;
  /** The result of building a tool. */
  final ToolSignature sig;
  ToolBoxResult(long t0, ToolSignature sig) {
    this.t0 = t0;
    this.sig = sig;
  }
}
