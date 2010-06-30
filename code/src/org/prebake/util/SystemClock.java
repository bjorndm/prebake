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

/**
 * A clock based upon {@link System#nanoTime()}.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class SystemClock implements Clock {
  private static final long NANOS_PER_MILLIS = 1000000L;
  private final long millisAtZeroNanos
      = System.currentTimeMillis() - (System.nanoTime() / NANOS_PER_MILLIS);

  private SystemClock() { /* singleton */ }
  public long nanoTime() { return System.nanoTime(); }
  @Override public String toString() { return "[SystemClock]"; }

  private static final SystemClock INSTANCE = new SystemClock();
  public static final SystemClock instance() { return INSTANCE; }
  public void toCalendar(long nanoTime, Calendar cal) {
    cal.setTimeInMillis(millisAtZeroNanos + nanoTime / NANOS_PER_MILLIS);
  }
}
