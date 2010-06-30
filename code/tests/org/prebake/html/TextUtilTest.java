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

package org.prebake.html;

import org.prebake.util.PbTestCase;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

public class TextUtilTest extends PbTestCase {
  @Test public final void testRomanNumerals() {
    ImmutableList.Builder<String> romanNumerals = ImmutableList.builder();
    for (int i = 0; i <= 30; ++i) {
      romanNumerals.add(TextUtil.toRomanNumeral(i));
    }
    assertEquals(
        ImmutableList.of(
            "",
            "I", "II", "III", "IV", "V",
            "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV",
            "XVI", "XVII", "XVIII", "XIX", "XX",
            "XXI", "XXII", "XXIII", "XXIV", "XXV",
            "XXVI", "XXVII", "XXVIII", "XXIX", "XXX"
            ),
        romanNumerals.build());
    assertEquals("XLVIII", TextUtil.toRomanNumeral(48));
    // Apparently this is more correct than IL.
    assertEquals("XLIX", TextUtil.toRomanNumeral(49));
    assertEquals("L", TextUtil.toRomanNumeral(50));
    assertEquals("LI", TextUtil.toRomanNumeral(51));
    assertEquals("XCVIII", TextUtil.toRomanNumeral(98));
    assertEquals("XCIX", TextUtil.toRomanNumeral(99));
    assertEquals("C", TextUtil.toRomanNumeral(100));
    assertEquals("CI", TextUtil.toRomanNumeral(101));
    assertEquals("CMXCVIII", TextUtil.toRomanNumeral(998));
    assertEquals("CMXCIX", TextUtil.toRomanNumeral(999));
    assertEquals("M", TextUtil.toRomanNumeral(1000));
    assertEquals("MI", TextUtil.toRomanNumeral(1001));
  }
}
