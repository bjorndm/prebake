/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: KeysIndex.java,v 1.14 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist;

import java.util.Map;
import java.util.SortedMap;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/**
 * The EntityIndex returned by SecondaryIndex.keysIndex().  This index maps
 * secondary key to primary key.  In Berkeley DB internal terms, this is a
 * secondary database that is opened without associating it with a primary.
 *
 * @author Mark Hayes
 */
class KeysIndex<SK, PK> extends BasicIndex<SK, PK> {

    private EntryBinding pkeyBinding;
    private SortedMap<SK, PK> map;

    KeysIndex(Database db,
              Class<SK> keyClass,
              EntryBinding keyBinding,
              Class<PK> pkeyClass,
              EntryBinding pkeyBinding)
        throws DatabaseException {

        super(db, keyClass, keyBinding,
              new DataValueAdapter<PK>(pkeyClass, pkeyBinding));
        this.pkeyBinding = pkeyBinding;
    }

    /*
     * Of the EntityIndex methods only get()/map()/sortedMap() are implemented
     * here.  All other methods are implemented by BasicIndex.
     */

    public PK get(SK key)
        throws DatabaseException {

        return get(null, key, null);
    }

    public PK get(Transaction txn, SK key, LockMode lockMode)
        throws DatabaseException {

        DatabaseEntry keyEntry = new DatabaseEntry();
        DatabaseEntry pkeyEntry = new DatabaseEntry();
        keyBinding.objectToEntry(key, keyEntry);

        OperationStatus status = db.get(txn, keyEntry, pkeyEntry, lockMode);

        if (status == OperationStatus.SUCCESS) {
            return (PK) pkeyBinding.entryToObject(pkeyEntry);
        } else {
            return null;
        }
    }

    public Map<SK, PK> map() {
        return sortedMap();
    }

    public synchronized SortedMap<SK, PK> sortedMap() {
        if (map == null) {
            map = new StoredSortedMap(db, keyBinding, pkeyBinding, true);
        }
        return map;
    }

    boolean isUpdateAllowed() {
        return false;
    }
}
