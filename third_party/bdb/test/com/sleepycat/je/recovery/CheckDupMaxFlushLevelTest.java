/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: CheckDupMaxFlushLevelTest.java,v 1.7 2010/01/04 15:51:03 cwl Exp $
 */
package com.sleepycat.je.recovery;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.Tree.SearchType;
import com.sleepycat.je.util.TestUtils;

public class CheckDupMaxFlushLevelTest extends CheckBase {

    private static final String DB_NAME = "foo";

    /**
     * Tests a fix for a bug in the way that the maxFlushLevel is used to
     * determine when to log a DIN root as provisional.  [#16712]
     *
     * Imagine this Btree.
     *
     *        IN-A
     *         |
     *       BIN-B
     *      /     \
     *    DIN-C   DIN-E
     *      |       |
     *   DBIN-D   DIN-F
     *              |
     *           DBIN-G
     *
     * When the checkpoint starts, DIN-C and DIN-E are the highest nodes that
     * are dirty.  So the max flush level is .... 3!
     *
     * DIN-C is level 2 and DIN-E is level 3.  They are at the same height
     * relative to the top, but not to the bottom.
     *
     * When we flush DIN-E, we make it non-provisional because its level (3) is
     * equal to the max flush level (3).
     *
     * But when we flush DIN-C, we make it provisional
     * (Provisional.BEFORE_CKPT_END to be exact, but provisional in effect)
     * because its level (2) is less than the max flush level (3).
     *
     * When we recover, we don't replay DIN-C because it is provisional.  So
     * any references it contains (or its children contain) that were necessary
     * for log cleaning, are lost.  If we deleted a log file based on those
     * lost references, we'll get LogFileNotFound.
     *
     * The solution is to log DIN-C non-provisionally, even though its level is
     * less than the max flush level.  It must be logged non-provisionally
     * when the parent's level is greater than the max flush level. 
     */
    public void testDupMaxFlushLevel()
        throws Throwable {

        final EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        turnOffEnvDaemons(envConfig);
        envConfig.setAllowCreate(true);
        turnOffEnvDaemons(envConfig);

        final DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setNodeMaxDupTreeEntries(4);

        setCheckLsns(false);

        testOneCase(DB_NAME,
                    envConfig,
                    dbConfig,
                    new TestGenerator(){
                        void generateData(Database db)
                            throws DatabaseException {

                            createMultiLevelDupTree(db);
                        }
                    },
                    envConfig,
                    dbConfig);
    }

    private void insert(Database db, int key, int data)
        throws DatabaseException {

        final DatabaseEntry keyEntry = new DatabaseEntry();
        IntegerBinding.intToEntry(key, keyEntry);
        final DatabaseEntry dataEntry = new DatabaseEntry();
        IntegerBinding.intToEntry(data, dataEntry);
        assertSame(OperationStatus.SUCCESS,
                   db.putNoDupData(null, keyEntry, dataEntry));
    }

    private void remove(Database db, int key, int data)
        throws DatabaseException {

        final DatabaseEntry keyEntry = new DatabaseEntry();
        IntegerBinding.intToEntry(key, keyEntry);
        final DatabaseEntry dataEntry = new DatabaseEntry();
        IntegerBinding.intToEntry(data, dataEntry);
        final Cursor c = db.openCursor(null, null);
        try {
            assertSame(OperationStatus.SUCCESS,
                       c.getSearchBoth(keyEntry, dataEntry, null));
            assertSame(OperationStatus.SUCCESS, c.delete());
        } finally {
            c.close();
        }
    }

    private void createMultiLevelDupTree(Database db)
        throws DatabaseException {

        final DatabaseImpl dbImpl = DbInternal.getDatabaseImpl(db);

        /* Create a 3 level dup tree for key 1. */
        for (int data = 1; getDupTreeDepth(dbImpl, 1) != 3; data += 1) {
            insert(db, 1, data);
        }

        /* Create a 2 level dup tree for key 2. */
        for (int data = 1; getDupTreeDepth(dbImpl, 2) != 2; data += 1) {
            insert(db, 2, data);
        }

        /* Flush all the way to the root. */
        final CheckpointConfig ckptConfig = new CheckpointConfig();
        ckptConfig.setForce(true);
        ckptConfig.setMinimizeRecoveryTime(true);
        env.checkpoint(ckptConfig);

        /*
         * Remove one entry for key 2, for which the DIN will be flushed
         * provisionally (incorrectly) when the bug occurs.
         */
        remove(db, 2, 1);

        /* Make both DIN roots dirty. */
        setDINRootDirty(dbImpl, 1);
        setDINRootDirty(dbImpl, 2);

        /*
         * Perform a normal checkpoint which should flush only up to the DIN
         * roots.  The bug causes the DIN root for key 2 to be incorrectly
         * logged as provisional.  During recovery, the removal of record (2,1)
         * will be lost.
         */
        ckptConfig.setForce(true);
        ckptConfig.setMinimizeRecoveryTime(false);
        env.checkpoint(ckptConfig);
    }

    private int getDupTreeDepth(DatabaseImpl dbImpl, int key)
        throws DatabaseException {

        final DIN din = getLatchedDINRoot(dbImpl, key);
        if (din == null) {
            return 0;
        }
        try {
            return din.getLevel();
        } finally {
            din.releaseLatch();
        }
    }

    private void setDINRootDirty(DatabaseImpl dbImpl, int key)
        throws DatabaseException {

        final DIN din = getLatchedDINRoot(dbImpl, key);
        assertNotNull(din);
        try {
            din.setDirty(true);
        } finally {
            din.releaseLatch();
        }
    }

    private DIN getLatchedDINRoot(DatabaseImpl dbImpl, int key)
        throws DatabaseException {

        final DatabaseEntry keyEntry = new DatabaseEntry();
        IntegerBinding.intToEntry(key, keyEntry);
        final byte[] keyBytes = keyEntry.getData();

        final BIN bin = (BIN) dbImpl.getTree().search
            (keyBytes, SearchType.NORMAL, -1, null, CacheMode.DEFAULT);
        if (bin == null) {
            return null;
        }
        try {
            final int idx = bin.findEntry(keyBytes, false, true);
            if (idx < 0) {
                return null;
            }
            final Node child = bin.fetchTarget(idx);
            if (!(child instanceof DIN)) {
                return null;
            }
            final DIN din = (DIN) child;
            assertNotNull(din);
            din.latch();
            return din;
        } finally {
            bin.releaseLatch();
        }
    }
}
