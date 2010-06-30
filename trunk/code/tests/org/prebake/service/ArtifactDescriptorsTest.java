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

package org.prebake.service;

import org.prebake.util.PbTestCase;

import org.junit.Test;

public class ArtifactDescriptorsTest extends PbTestCase {
  @Test public final void testUnambiguousFileName() {
    assertEquals(".foo", ufn(""));  // We never emit the empty string.
    assertEquals("+2e+.foo", ufn("."));  // We never emit . or ..
    assertEquals("+2e.2e+.foo", ufn(".."));  // We never emit . or ..
    assertEquals("bar.foo", ufn("bar"));
    // Produce only filenames with no uppercase chars for Windows compat.
    assertEquals("bb.arr.foo", ufn("bbArr"));
    assertEquals(
        "+28.2f.3a+shell+3b.5c.2a+-+3c+specials+3e.29+.foo",
        ufn("(/:shell;\\*-<specials>)"));
  }

  private static String ufn(String s) {
    return ArtifactDescriptors.toUnambiguousFileName(s, ".foo");
  }
}
