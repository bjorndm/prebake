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

package org.prebake.service.plan;

/**
 * Thrown when the user tries to create a recipe for a product that does exist,
 * or a recipe would require deriving a product from a template based on
 * incomplete information.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public class MissingProductException extends Exception {
  public MissingProductException(String msg) { super(msg); }
}
