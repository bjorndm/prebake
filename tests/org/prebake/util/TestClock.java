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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public final class TestClock implements Clock {
  private long t = 1234;
  private static final long TEST_EPOCH;
  static {
    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    c.setTimeInMillis(0);
    c.set(Calendar.YEAR, 1990);
    c.set(Calendar.MONTH, Calendar.JANUARY);
    c.set(Calendar.DAY_OF_MONTH, 1);
    TEST_EPOCH = c.getTimeInMillis();
  }

  public long nanoTime() { return t; }

  public void advance(long deltaNanos) {
    assert deltaNanos >= 0;
    t += deltaNanos;
  }

  public void toCalendar(long nanoTime, Calendar cal) {
    cal.setTimeInMillis(TEST_EPOCH + nanoTime / (1000000L /* ns/ms */));
  }
}
