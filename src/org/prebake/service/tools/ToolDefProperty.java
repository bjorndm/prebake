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
 * JavaScript object property names used in the output of a tool file.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public enum ToolDefProperty {
  /** The name of a documentation string. */
  help,
  /**
   * The name of a function that sanity checks a plan file product
   * that uses this rule.
   */
  check,
  /**
   * The name of a function that uses the tool to build a product.
   */
  fire,
  ;
}
