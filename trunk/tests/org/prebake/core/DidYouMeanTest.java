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

package org.prebake.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DidYouMeanTest {
  @Test public final void testToMessage() {
    assertEquals(
        "Foo. Did you mean \"cookies\"?",
        DidYouMean.toMessage(
            "Foo", "cake", "ice cream", "cookies", "apple pie"));
    assertEquals(
        "Bad flag -Foo. Did you mean \"--foo\"?",
        DidYouMean.toMessage(
            "Bad flag -Foo", "--Foo",
            "--foo", "--bar", "--baz"));
    assertEquals(
        "Bad flag -Bar. Did you mean \"--bar\"?",
        DidYouMean.toMessage(
            "Bad flag -Bar", "--Bar",
            "--foo", "--bar", "--baz"));
    assertEquals(
        "Bad flag -Baz. Did you mean \"--baz\"?",
        DidYouMean.toMessage(
            "Bad flag -Baz", "--Baz",
            "--foo", "--bar", "--baz"));
  }
}
