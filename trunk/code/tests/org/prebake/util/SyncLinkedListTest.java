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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SyncLinkedListTest extends PbTestCase {
  SyncLinkedList<NumberNode> list;

  @Before public void setUp() {
    this.list = new SyncLinkedList<NumberNode>();
  }

  @After public void tearDown() {
    // Sanity checks.
    NumberNode last = null;
    for (NumberNode n = list.first(); n != null; n = list.after(n)) {
      assertTrue(n.inList);
      assertSame(last, n.prev);
      last = n;
    }
    if (last != null) { assertNull(last.next); }
    list = null;
  }

  @Test public final void testEmptyList() {
    assertNull(list.first());
  }

  @Test public final void testListIteration() {
    list.add(node(1));
    list.add(node(2));
    list.add(node(3));
    NumberNode n = list.first();
    assertEquals(1, n.n);
    n = list.after(n);
    assertEquals(2, n.n);
    n = list.after(n);
    assertEquals(3, n.n);
    assertNull(list.after(n));
  }

  @Test public final void testListIterationWithDeletionAndInsertion() {
    list.add(node(1));
    list.add(node(2));
    list.add(node(3));
    NumberNode n = list.first();
    assertEquals(1, n.n);
    list.remove(n);
    // We can still iterate to the next
    n = list.after(n);
    assertEquals(2, n.n);
    n = list.after(n);
    list.add(node(4));
    assertEquals(3, n.n);
    n = list.after(n);
    assertEquals(4, n.n);
    assertNull(list.after(n));
  }

  @Test public final void testListIterationWithMultipleDeletions() {
    NumberNode a = node(1), b = node(2), c = node(3), d = node(4);
    list.add(a);
    list.add(b);
    list.add(c);
    list.add(d);

    NumberNode n = list.first();
    assertEquals(1, n.n);
    list.remove(b);
    list.remove(c);
    n = list.after(n);
    assertEquals(4, n.n);
    assertNull(list.after(n));
  }

  @Test public final void testListIterationWithMultipleInclSelfDeletions() {
    NumberNode a = node(1), b = node(2), c = node(3), d = node(4);
    list.add(a);
    list.add(b);
    list.add(c);
    list.add(d);

    NumberNode n = list.first();
    assertEquals(1, n.n);
    list.remove(b);
    list.remove(a);
    list.remove(c);
    n = list.after(n);
    assertEquals(4, n.n);
    assertNull(list.after(n));
  }

  static final class NumberNode extends SyncListElement<NumberNode> {
    final int n;
    NumberNode(int n) { this.n = n; }

    @Override public String toString() { return "" + n; }
  }
  static final NumberNode node(int n) { return new NumberNode(n); }
}
