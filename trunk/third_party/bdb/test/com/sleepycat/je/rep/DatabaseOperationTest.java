/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DatabaseOperationTest.java,v 1.35 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.VLSN;

/**
 * Check that database operations are properly replicated.
 */
public class DatabaseOperationTest extends TestCase {

    private final File envRoot;
    private final String[] dbNames = new String[] {"DbA", "DbB"};
    private RepEnvInfo[] repEnvInfo;
    private Map<String, TestDb> expectedResults;
    private final boolean verbose = Boolean.getBoolean("verbose");

    public DatabaseOperationTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    public void tearDown() {
        try {
            RepTestUtils.removeRepEnvironments(envRoot);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Check that master->replica replication of database operations work.
     */
    public void testBasic()
        throws Exception {

        expectedResults = new HashMap<String, TestDb>();

        try {
            repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 2);
            ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);

            execDatabaseOperations(master);
            checkEquality(repEnvInfo);

            doMoreDatabaseOperations(master, repEnvInfo);
        } finally {
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    /**
     * Check that master->replica replication of database operations work, and
     * also verify that the client has logged enough information to act
     * as the master later on.
     */
    public void testCascade()
        throws Exception {

        expectedResults = new HashMap<String, TestDb>();

        try {
            repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 5);

            /* Open all the replicated environments and select a master. */
            ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
            /* Shutdown a replica. */
            for (RepEnvInfo repInfo : repEnvInfo) {
                if (repInfo.getEnv().getState().isReplica()) {
                    repInfo.closeEnv();
                    break;
                }
            }

            /* Record the former master id. */
            int formerMasterId = RepInternal.getNodeId(master);
            /* Do some database work. */
            execDatabaseOperations(master);
            /* Sync the replicators and shutdown the master. */
            checkEquality(RepTestUtils.getOpenRepEnvs(repEnvInfo));
            for (RepEnvInfo repInfo: repEnvInfo) {
                if (repInfo.getEnv() != null &&
                    repInfo.getEnv().getState().isMaster()) {
                    repInfo.closeEnv();
                    break;
                }
            }

            /* Find out the new master for those open replicators. */
            master = RepTestUtils.openRepEnvsJoin(repEnvInfo);
            /* Make sure the master is not the former one. */
            assertTrue(formerMasterId != RepInternal.getNodeId(master));
            doMoreDatabaseOperations(master,
                                     RepTestUtils.getOpenRepEnvs(repEnvInfo));

            /* Re-open closed replicators and check the node equality. */
            master = RepTestUtils.joinGroup(repEnvInfo);
            /* Verify the new master is different from the first master. */
            assertTrue(formerMasterId != RepInternal.getNodeId(master));
            assertEquals(RepTestUtils.getOpenRepEnvs(repEnvInfo).length,
                         repEnvInfo.length);
            checkEquality(repEnvInfo);
        } finally {
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    /* Truncate, rename and remove databases on replicators. */
    private void doMoreDatabaseOperations(ReplicatedEnvironment master,
                                          RepEnvInfo[] repInfoArray)
        throws Exception {

        for (String dbName : dbNames) {
            truncateDatabases(master, dbName, repInfoArray);
            master.renameDatabase(null, dbName, "new" + dbName);
            checkEquality(repInfoArray);
            master.removeDatabase(null, "new" + dbName);
            checkEquality(repInfoArray);
        }
    }

    /**
     * Execute a variety of database operations on this node.
     */
    @SuppressWarnings("unchecked")
    private void execDatabaseOperations(ReplicatedEnvironment env)
        throws Exception {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(false);

        /* Make a vanilla database and add some records. */
        Database db = env.openDatabase(null, dbNames[0], dbConfig);
        insertData(db);
        expectedResults.put(dbNames[0],
                            new TestDb(db.getConfig(), db.count()));
        db.close();

        /* Make a database with comparators */
        dbConfig.setBtreeComparator(new FooComparator());
        dbConfig.setDuplicateComparator
            ((Class<Comparator<byte[]>>)
             Class.forName("com.sleepycat.je.rep." +
                           "DatabaseOperationTest$BarComparator"));
        db = env.openDatabase(null, dbNames[1], dbConfig);
        expectedResults.put(dbNames[1],
                            new TestDb(db.getConfig(), db.count()));
        db.close();
    }

    /* Insert some data for truncation verfication. */
    private void insertData(Database db)
        throws Exception {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < 10; i++) {
            IntegerBinding.intToEntry(i, key);
            StringBinding.stringToEntry("herococo", data);
            db.put(null, key, data);
        }
    }

    /*
     * Truncate the database on the master and check whether the db.count
     * is 0 after truncation.
     */
    private void truncateDatabases(ReplicatedEnvironment master,
                                   String dbName,
                                   RepEnvInfo[] repInfoArray)
        throws Exception {

        /* Check the correction of db.count before truncation. */
        long expectedCount = expectedResults.get(dbName).count;
        DatabaseConfig dbConfig =
            expectedResults.get(dbName).dbConfig.cloneConfig();
        checkCount(repInfoArray, dbName, dbConfig, expectedCount);

        /* Truncate the database and do the check. */
        master.truncateDatabase(null, dbName, true);
        /* Do the sync so that the replicators do the truncation. */
        RepTestUtils.syncGroupToLastCommit(repInfoArray, repInfoArray.length);
        checkCount(repInfoArray, dbName, dbConfig, 0);
        checkEquality(repInfoArray);
    }

    /* Check that the number of records in the database is correct */
    private void checkCount(RepEnvInfo[] repInfoArray,
                            String dbName,
                            DatabaseConfig dbConfig,
                            long dbCount)
        throws Exception {

        for (RepEnvInfo repInfo : repInfoArray) {
            Database db =
                repInfo.getEnv().openDatabase(null, dbName, dbConfig);
            assertEquals(dbCount, db.count());
            db.close();
        }
    }

    private void checkEquality(RepEnvInfo[] repInfoArray)
        throws Exception {

        VLSN vlsn = RepTestUtils.syncGroupToLastCommit(repInfoArray,
                                                       repInfoArray.length);
        RepTestUtils.checkNodeEquality(vlsn, verbose, repInfoArray);
    }

    /**
     * Keep track of the database name and other characteristics, to
     * be used in validating data.
     */
    static class TestDb {
        DatabaseConfig dbConfig;
        long count;

        TestDb(DatabaseConfig dbConfig, long count) {
            this.dbConfig = dbConfig.cloneConfig();
            this.count = count;
        }
    }

    /**
     * A placeholder comparator class, just for testing whether comparators
     * replicate properly.
     */
    @SuppressWarnings("serial")
    public static class FooComparator implements Comparator<byte[]>,
                                                 Serializable {

        public FooComparator() {
        }

        public int compare(@SuppressWarnings("unused") byte[] o1,
                           @SuppressWarnings("unused") byte[] o2) {
            /* No need to really fill in. */
            return 0;
        }
    }

    /**
     * A placeholder comparator class, just for testing whether comparators
     * replicate properly.
     */
    @SuppressWarnings("serial")
    public static class BarComparator implements Comparator<byte[]>,
                                                 Serializable {
        public BarComparator() {
        }

        public int compare(@SuppressWarnings("unused") byte[] arg0,
                           @SuppressWarnings("unused") byte[] arg1) {
            /* No need to really fill in. */
            return 0;
        }
    }
}
