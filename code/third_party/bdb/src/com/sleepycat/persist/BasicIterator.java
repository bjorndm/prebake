/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: BasicIterator.java,v 1.15 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.util.RuntimeExceptionWrapper;

/**
 * Implements Iterator for an arbitrary EntityCursor.
 *
 * @author Mark Hayes
 */
class BasicIterator<V> implements Iterator<V> {

    private EntityCursor<V> entityCursor;
    private ForwardCursor<V> forwardCursor;
    private LockMode lockMode;
    private V nextValue;

    /**
     * An EntityCursor is given and the remove() method is supported.
     */
    BasicIterator(EntityCursor<V> entityCursor, LockMode lockMode) {
        this.entityCursor = entityCursor;
        this.forwardCursor = entityCursor;
        this.lockMode = lockMode;
    }

    /**
     * A ForwardCursor is given and the remove() method is not supported.
     */
    BasicIterator(ForwardCursor<V> forwardCursor, LockMode lockMode) {
        this.forwardCursor = forwardCursor;
        this.lockMode = lockMode;
    }

    public boolean hasNext() {
        if (nextValue == null) {
            try {
                nextValue = forwardCursor.next(lockMode);
            } catch (DatabaseException e) {
                throw RuntimeExceptionWrapper.wrapIfNeeded(e);
            }
            return nextValue != null;
        } else {
            return true;
        }
    }

    public V next() {
        if (hasNext()) {
            V v = nextValue;
            nextValue = null;
            return v;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        if (entityCursor == null) {
            throw new UnsupportedOperationException();
        }
        try {
            if (!entityCursor.delete()) {
                throw new IllegalStateException
                    ("Record at cursor position is already deleted");
            }
        } catch (DatabaseException e) {
            throw RuntimeExceptionWrapper.wrapIfNeeded(e);
        }
    }
}
