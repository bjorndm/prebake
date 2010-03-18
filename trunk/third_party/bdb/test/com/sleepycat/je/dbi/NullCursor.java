/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NullCursor.java,v 1.23 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.dbi;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.txn.Locker;

/**
 * A NullCursor is used as a no-op object by tree unit tests, which
 * wish to speak directly to Tree methods.
 */
public class NullCursor extends CursorImpl {
    /**
     * Cursor constructor.
     */
    public NullCursor(DatabaseImpl database, Locker txn) {
        super(database, txn);
    }

    @Override
    public void addCursor(BIN bin) {}
    @Override
    public void addCursor() {}
}
