/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2010 Oracle.  All rights reserved.
 *
 * $Id: RepIDSequenceTest.java,v 1.4 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.VLSN;

public class RepIDSequenceTest extends TestCase {
    private File envRoot;
    private RepEnvInfo[] repEnvInfo;
    private final int DB_NUM = 5;
    private final int DB_SIZE = 100;
    private final String DB_NAME = "test";
    private boolean verbose = Boolean.getBoolean("verbose");

    public RepIDSequenceTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    public void tearDown() 
        throws Exception {

        RepTestUtils.shutdownRepEnvs(repEnvInfo);
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    /* Verify that id generation is in sequence after recovery. */
    public void testIDSequenceAfterRecovery()
        throws Exception {

        repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 1);
        /* Get configurations so that they can be used when reopened.*/ 
        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
        ReplicationConfig repConfig = master.getRepConfig();
        EnvironmentConfig envConfig = master.getConfig();
        File envHome = master.getHome();
        /* Do some operations. */
        doOperations(master, 1, DB_NUM);
        /* Do a sync to make sure the ids are written to the log. */
        master.sync();
        RepImpl repImpl = RepInternal.getRepImpl(master);
        long lastRepNodeId = repImpl.getNodeSequence().getLastReplicatedNodeId();
        long lastDbId = repImpl.getDbTree().getLastReplicatedDbId();
        long lastTxnId = repImpl.getTxnManager().getLastReplicatedTxnId();
        /* Make sure that replicated ids are negative ids. */
        assertTrue(lastRepNodeId < 0);
        assertTrue(lastDbId < 0);
        assertTrue(lastTxnId < 0);
        master.close();

        master = new ReplicatedEnvironment(envHome, repConfig, envConfig);
        assertTrue(master.getState().isMaster());
        repImpl = RepInternal.getRepImpl(master);

        /* 
         * The node id, db id and txn id should be the same as before, since 
         * the test doesn't do any operations during this time.
         */
        assertEquals(lastRepNodeId, 
                     repImpl.getNodeSequence().getLastReplicatedNodeId());
        assertEquals(lastDbId,
                     repImpl.getDbTree().getLastReplicatedDbId());
        assertEquals(lastTxnId,
                     repImpl.getTxnManager().getLastReplicatedTxnId());
        doOperations(master, DB_NUM + 1, DB_NUM + 1);

        /*
         * The node id, db id and txn id should be smaller than before, since
         * test creats a new database, db id should be 1 smaller than before.
         */
        assertTrue(repImpl.getNodeSequence().getLastReplicatedNodeId() < 
                   lastRepNodeId);
        assertTrue
            (repImpl.getDbTree().getLastReplicatedDbId() == lastDbId - 1);
        assertTrue
            (repImpl.getTxnManager().getLastReplicatedTxnId() < lastTxnId);
        master.close();
    }

    /* Verify that ids are in sequence after fail over. */
    public void testIDSequenceAfterFailOver()
        throws Exception {

        repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 3);
        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
        doOperations(master, 1, DB_NUM);
        master.sync();
        RepImpl repImpl = RepInternal.getRepImpl(master);
        long lastRepNodeId = repImpl.getNodeSequence().getLastReplicatedNodeId();
        long lastDbId = repImpl.getDbTree().getLastReplicatedDbId();
        long lastTxnId = repImpl.getTxnManager().getLastReplicatedTxnId();
        /* Make sure that replicated ids are negative ids. */
        assertTrue(lastRepNodeId < 0);
        assertTrue(lastDbId < 0);
        assertTrue(lastTxnId < 0);
        checkEquality(repEnvInfo);
        master.close();

        master = RepTestUtils.openRepEnvsJoin(repEnvInfo);
        assertTrue(master.getState().isMaster());
        repImpl = RepInternal.getRepImpl(master);
        assertEquals(lastRepNodeId,
                     repImpl.getNodeSequence().getLastReplicatedNodeId());
        assertEquals(lastDbId,
                     repImpl.getDbTree().getLastReplicatedDbId());

        /* 
         * The replication group needs to do elections to select a master, so
         * the txn id should be different as before. 
         */
        assertTrue
            (repImpl.getTxnManager().getLastReplicatedTxnId() < lastTxnId);
        /* Create a new database. */
        doOperations(master, DB_NUM + 1, DB_NUM + 1);
        assertTrue(repImpl.getNodeSequence().getLastReplicatedNodeId() <
                   lastRepNodeId);
        assertTrue
            (repImpl.getDbTree().getLastReplicatedDbId() == lastDbId - 1);
        assertTrue
            (repImpl.getTxnManager().getLastReplicatedTxnId() < lastTxnId);
        checkEquality(RepTestUtils.getOpenRepEnvs(repEnvInfo));
    }

    private void doOperations(ReplicatedEnvironment master,
                              int beginId,
                              int endId) 
        throws Exception {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(false);

        for (int i = beginId; i <= endId; i++) {
            Database db = master.openDatabase
                (null, DB_NAME + new Integer(i).toString(), dbConfig);
            insertData(db);
            db.close();
        }
    }

    private void insertData(Database db) 
        throws Exception {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 1; i <= DB_SIZE; i++) {
            IntegerBinding.intToEntry(i, key);
            StringBinding.stringToEntry("herococo", data);
            db.put(null, key, data);
        }
    }

    private void checkEquality(RepEnvInfo[] repInfoArray)
        throws Exception {

        VLSN vlsn = RepTestUtils.syncGroupToLastCommit(repInfoArray,
                                                       repInfoArray.length);
        RepTestUtils.checkNodeEquality(vlsn, verbose, repInfoArray);
    }
}
