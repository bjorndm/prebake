/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ForeignKeyTrigger.java,v 1.11 2010/01/04 15:50:36 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.txn.Locker;

class ForeignKeyTrigger implements DatabaseTrigger {

    private SecondaryDatabase secDb;

    ForeignKeyTrigger(SecondaryDatabase secDb) {

        this.secDb = secDb;
    }

    public void triggerAdded(Database db) {
    }

    public void triggerRemoved(Database db) {

        secDb.clearForeignKeyTrigger();
    }

    public void databaseUpdated(Database db,
                                Locker locker,
                                DatabaseEntry priKey,
                                DatabaseEntry oldData,
                                DatabaseEntry newData)
        throws DatabaseException {

        if (newData == null) {
            secDb.onForeignKeyDelete(locker, priKey);
        }
    }
}
