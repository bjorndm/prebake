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

package org.prebake.util;

import java.util.List;

import junit.framework.ComparisonFailure;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public final class MoreAsserts {
  public static void assertContainsInOrder(
      String[] actual, String... required) {
    assertContainsInOrder(ImmutableList.copyOf(actual), required);
  }

  public static void assertContainsInOrder(
      List<String> actual, String... required) {
    int pos = 0;
    for (String req : required) {
      int idx = actual.subList(pos, actual.size()).indexOf(req);
      if (idx < 0) {
        throw new ComparisonFailure(
            "Did not find " + req,
            Joiner.on('\n').join(required),
            Joiner.on('\n').join(actual));
      }
      pos = idx;
    }
  }
}
