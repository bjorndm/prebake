package org.prebake.util;

import javax.annotation.Nullable;

/**
 * An element in a {@link SyncLinkedList}.  Override this class to make it
 * carry data.
 *
 * @author Mike Samuel <mikesamuel@gmail.com>
 */
public abstract class SyncListElement<T extends SyncListElement<T>> {
  // These fields are for SyncLinkedLists bookkeeping and should not be modified
  // by other classes.
  @Nullable T prev;
  @Nullable T next;
  boolean inList;
}
