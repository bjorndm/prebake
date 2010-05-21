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

import javax.annotation.Nullable;

//TODO: the code below could probably use a few extra pairs of eyes.
/**
 * This is a quick and dirty synchronized linked list that provides O(1)
 * insert and remove operations, and single-direction traversal that is O(1)
 * amortized and O(n) non-amortized worst-case where n is the number of items
 * that have been removed since last traversal.
 * (The amortized time assumes traversals are at least as common as removals.)
 *
 * <h2>Caveats</h2>
 * The operations below are synchronized per list, so if elements are moved
 * from one list to another, the mover must be synchronized externally on both
 * lists.
 * Best not to do that.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public final class SyncLinkedList<T extends SyncListElement<T>> {
  private @Nullable T head;
  private @Nullable T tail;

  // Invariants:
  // 1. el.inList <-> el is reachable by next link traversal from head.
  // 2. el.inList <-> el is reachable by prev link traversal from tail.
  // 3. el.prev == null && el.inList <-> el == head
  // 4. el.next == null && el.inList <-> el == tail
  // 5. el.prev != null && el.inList -> el.prev.next == el && el.prev.inList
  // 6. el.next != null && el.inList -> el.next.prev == el && el.next.inList

  /**
   * The first element on the list.
   * @return null if the list is empty.
   */
  public synchronized @Nullable T first() { return head; }

  /**
   * The next element on the list.  If el has been removed, then this returns
   * the next item that was after it that has not also been removed.
   * @return null if nothing after el.
   */
  public synchronized @Nullable T after(T el) {
    while (el.next != null && !el.next.inList) { el = el.next; }
    return el.next;
  }

  public synchronized void remove(T el) {
    if (!el.inList) { throw new IllegalStateException(); }
    // snapshot
    T prev = el.prev;
    T next = el.next;

    if (prev != null) {
      prev.next = next;
    } else {
      head = next;
    }
    // Now the next pointers are consistent.
    if (next != null) {
      next.prev = prev;
    } else {
      tail = prev;
    }
    // Now the prev pointers are consistent
    el.prev = null;  // Not invariant maintaining, but good for GC.
    el.inList = false;
    // Now inList invariants are maintained.
  }

  public synchronized void add(T el) {
    if (el.inList) { throw new IllegalStateException(); }
    if (tail == null) {
      head = el;
    } else {
      tail.next = el;
    }
    el.next = null;
    // Now next pointers are consistent.
    el.prev = tail;
    tail = el;
    // Now prev pointers are consistent.
    el.inList = true;
    // Now inList -> reachable from head/tail is maintained.
  }
}
