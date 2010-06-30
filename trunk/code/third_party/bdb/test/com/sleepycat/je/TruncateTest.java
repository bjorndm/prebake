/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TruncateTest.java,v 1.33 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.DualTestCase;
import com.sleepycat.je.util.TestUtils;

/**
 * Basic database operations, excluding configuration testing.
 */
public class TruncateTest extends DualTestCase {
    private static final int NUM_RECS = 257;
    private static final String DB_NAME = "testDb";

    private File envHome;
    private Environment env;

    public TruncateTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws Exception {

        super.setUp();

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    @Override
    public void tearDown()
        throws Exception {

        if (env != null) {
            try {
                /* Close in case we hit an exception and didn't close. */
                close(env);
            } catch (Exception e) {
                /* Ok if already closed */
            }
        }
        env = null; // for JUNIT, to reduce memory usage when run in a suite.
        super.tearDown();
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testEnvTruncateAbort()
        throws Throwable {

        doTruncateAndAdd(true,    // transactional
                         256,     // step1 num records
                         false,   // step2 autocommit
                         150,     // step3 num records
                         true,    // step4 abort
                         0);      // step5 num records
    }

    public void testEnvTruncateCommit()
        throws Throwable {

        doTruncateAndAdd(true,    // transactional
                         256,     // step1 num records
                         false,   // step2 autocommit
                         150,     // step3 num records
                         false,   // step4 abort
                         150);    // step5 num records
    }

    public void testEnvTruncateAutocommit()
        throws Throwable {

        doTruncateAndAdd(true,    // transactional
                         256,     // step1 num records
                         true,    // step2 autocommit
                         150,     // step3 num records
                         false,   // step4 abort
                         150);    // step5 num records
    }

    public void testEnvTruncateNoFirstInsert()
        throws Throwable {

        doTruncateAndAdd(true,    // transactional
                         0,       // step1 num records
                         false,   // step2 autocommit
                         150,     // step3 num records
                         false,   // step4 abort
                         150);    // step5 num records
    }

    public void testNoTxnEnvTruncateCommit()
        throws Throwable {

        doTruncateAndAdd(false,    // transactional
                         256,      // step1 num records
                         false,    // step2 autocommit
                         150,      // step3 num records
                         false,    // step4 abort
                         150);     // step5 num records
    }

    public void testTruncateCommit()
        throws Throwable {

        doTruncate(false, false);
    }

    public void testTruncateCommitAutoTxn()
        throws Throwable {

        doTruncate(false, true);
    }

    public void testTruncateAbort()
        throws Throwable {

        doTruncate(true, false);
    }

    /*
     * SR 10386, 11252. This used to deadlock, because the truncate did not
     * use an AutoTxn on the new mapLN, and the put operations conflicted with
     * the held write lock.
     */
    public void testWriteAfterTruncate()
        throws Throwable {

        try {
            Database myDb = initEnvAndDb(true);

            myDb.close();
            Transaction txn = env.beginTransaction(null, null);
            long truncateCount = env.truncateDatabase(txn, DB_NAME, true);
            assertEquals(0, truncateCount);
            txn.commit();
            close(env);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testTruncateEmptyDeferredWriteDatabase()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(false);
            envConfig.setConfigParam
                (EnvironmentParams.ENV_CHECK_LEAKS.getName(), "false");
            envConfig.setAllowCreate(true);
            env = create(envHome, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(false);
            dbConfig.setSortedDuplicates(true);
            dbConfig.setAllowCreate(true);
            dbConfig.setDeferredWrite(true);
            Database myDb = env.openDatabase(null, DB_NAME, dbConfig);
            myDb.close();
            long truncateCount;
            truncateCount = env.truncateDatabase(null, DB_NAME, true);
            assertEquals(0, truncateCount);
        } catch (Throwable T) {
            T.printStackTrace();
            throw T;
        }
    }

    public void testTruncateNoLocking()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(false);
            envConfig.setConfigParam
                (EnvironmentConfig.ENV_IS_LOCKING, "false");
            envConfig.setAllowCreate(true);
            env = create(envHome, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(false);
            dbConfig.setAllowCreate(true);
            Database myDb = env.openDatabase(null, DB_NAME, dbConfig);
            myDb.put(null, new DatabaseEntry(new byte[0]),
                           new DatabaseEntry(new byte[0]));
            myDb.close();
            long truncateCount;
            truncateCount = env.truncateDatabase(null, DB_NAME, true);
            assertEquals(1, truncateCount);
        } catch (Throwable T) {
            T.printStackTrace();
            throw T;
        }
    }

    /**
     * 1. Populate a database.
     * 2. Truncate.
     * 3. Commit or abort.
     * 4. Check that database has the right amount of records.
     */
    private void doTruncate(boolean abort, boolean useAutoTxn)
        throws Throwable {

        try {
            int numRecsAfterTruncate =
                useAutoTxn ? 0 : ((abort) ? NUM_RECS : 0);
            Database myDb = initEnvAndDb(true);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();

            /* Populate database. */
            for (int i = NUM_RECS; i > 0; i--) {
                key.setData(TestUtils.getTestArray(i));
                data.setData(TestUtils.getTestArray(i));
                assertEquals(OperationStatus.SUCCESS,
                             myDb.put(null, key, data));
            }

            /* Truncate, check the count, commit. */
            myDb.close();
            long truncateCount = 0;
            if (useAutoTxn) {
                truncateCount = env.truncateDatabase(null, DB_NAME, true);
            } else {
                Transaction txn = env.beginTransaction(null, null);
                truncateCount = env.truncateDatabase(txn, DB_NAME, true);

                if (abort) {
                    txn.abort();
                } else {
                    txn.commit();
                }
            }

            assertEquals(NUM_RECS, truncateCount);

            /* Do a cursor read, make sure there's the right amount of data. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setSortedDuplicates(true);
            dbConfig.setTransactional(true);
            myDb = env.openDatabase(null, DB_NAME, dbConfig);
            int count = 0;
            Transaction txn = null;
            if (DualTestCase.isReplicatedTest(getClass())) {
                txn = env.beginTransaction(null, null);
            }
            Cursor cursor = myDb.openCursor(txn, null);
            while (cursor.getNext(key, data, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
                count++;
            }
            assertEquals(numRecsAfterTruncate, count);
            cursor.close();
            if (txn != null) {
                txn.commit();
            }

            /* Recover the database. */
            myDb.close();
            close(env);
            myDb = initEnvAndDb(true);

            /* Check data after recovery. */
            count = 0;
            if (DualTestCase.isReplicatedTest(getClass())) {
                txn = env.beginTransaction(null, null);
            }
            cursor = myDb.openCursor(txn, null);
            while (cursor.getNext(key, data, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
                count++;
            }
            assertEquals(numRecsAfterTruncate, count);
            cursor.close();
            if (txn != null) {
                txn.commit();
            }
            myDb.close();
            close(env);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * This method can be configured to execute a number of these steps:
     * - Populate a database with 0 or N records

     * 2. Truncate.
     * 3. add more records
     * 4. abort or commit
     * 5. Check that database has the right amount of records.
     */
    private void doTruncateAndAdd(boolean transactional,
                                  int step1NumRecs,
                                  boolean step2AutoCommit,
                                  int step3NumRecs,
                                  boolean step4Abort,
                                  int step5NumRecs)
        throws Throwable {

        String databaseName = "testdb";
        try {
            /* Use enough records to force a split. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(transactional);
            envConfig.setAllowCreate(true);
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     "6");
            env = create(envHome, envConfig);

            /* Make a db and open it. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(transactional);
            dbConfig.setAllowCreate(true);
            Database myDb = env.openDatabase(null, databaseName, dbConfig);

            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();

            /* Populate database with step1NumRecs. */
            Transaction txn = null;
            if (transactional) {
                txn = env.beginTransaction(null, null);
            }
            for (int i = 0; i < step1NumRecs; i++) {
                IntegerBinding.intToEntry(i, key);
                IntegerBinding.intToEntry(i, data);
                assertEquals(OperationStatus.SUCCESS,
                             myDb.put(txn, key, data));
            }

            myDb.close();

            /* Truncate. Possibly autocommit*/
            if (step2AutoCommit && transactional) {
                txn.commit();
                txn = null;
            }

            /*
             * Before truncate, there should be two databases in the system:
             * the testDb database, and the FileSummary database.
             */
            countLNs(2, 2);
            long truncateCount = env.truncateDatabase(txn, databaseName, true);
            assertEquals(step1NumRecs, truncateCount);

            /*
             * The naming tree should always have two entries now, the
             * mapping tree might have 2 or 3, depending on abort.
             */
            if (step2AutoCommit || !transactional) {
                countLNs(2, 2);
            } else {
                countLNs(2, 3);
            }

            /* Add more records. */
            myDb = env.openDatabase(txn, databaseName, dbConfig);
            checkCount(myDb, txn, 0);
            for (int i = 0; i < step3NumRecs; i++) {
                IntegerBinding.intToEntry(i, key);
                IntegerBinding.intToEntry(i, data);
                assertEquals(OperationStatus.SUCCESS,
                             myDb.put(txn, key, data));
            }

            checkCount(myDb, txn, step3NumRecs);
            myDb.close();

            if (txn != null) {
                if (step4Abort) {
                    txn.abort();
                } else {
                    txn.commit();

                }
            }
            /* Now the mapping tree should only have two entries. */
            countLNs(2, 2);

            /* Do a cursor read, make sure there's the right amount of data. */
            myDb = env.openDatabase(null, databaseName, dbConfig);
            checkCount(myDb, null, step5NumRecs);
            myDb.close();
            close(env);

            /* Check data after recovery. */
            env = create(envHome, envConfig);
            myDb = env.openDatabase(null, databaseName, dbConfig);
            checkCount(myDb, null, step5NumRecs);
            myDb.close();
            close(env);

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test that truncateDatabase and removeDatabase can be called after
     * replaying an LN in that database during recovery.  This is to test a fix
     * to a bug where truncateDatabase caused a hang because DbTree.releaseDb
     * was not called by RecoveryUtilizationTracker.  [#16329]
     */
    public void testTruncateAfterRecovery()
        throws Throwable {

        DatabaseEntry key = new DatabaseEntry(new byte[10]);
        DatabaseEntry data = new DatabaseEntry(new byte[10]);

        Database db = initEnvAndDb(true);
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);

        /* Write a single record for recovery. */
        OperationStatus status = db.put(null, key, data);
        assertSame(OperationStatus.SUCCESS, status);

        /* Close without a checkpoint and run recovery. */
        db.close();
        envImpl.abnormalClose();
        envImpl = null;
        env = null;
        db = initEnvAndDb(true);

        /* Ensure that truncateDatabase does not hang. */
        db.close();
        long truncateCount = env.truncateDatabase(null, DB_NAME, true);
        assertEquals(1, truncateCount);

        /* removeDatabase should also work. */
        env.removeDatabase(null, DB_NAME);
        assertTrue(!env.getDatabaseNames().contains(DB_NAME));

        close(env);
        env = null;
    }

    /**
     * Set up the environment and db.
     */
    private Database initEnvAndDb(boolean isTransactional)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(isTransactional);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_CHECK_LEAKS.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        env = create(envHome, envConfig);

        /* Make a db and open it. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(isTransactional);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setAllowCreate(true);
        Database myDb = env.openDatabase(null, DB_NAME, dbConfig);
        return myDb;
    }

    private void checkCount(Database db, Transaction txn, int expectedCount)
        throws DatabaseException {

        Cursor cursor = db.openCursor(txn, null);
        int count = 0;
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS) {
            count++;
        }
        assertEquals(expectedCount, count);
        cursor.close();
    }

    /**
     * Use stats to count the number of LNs in the id and name mapping
     * trees. It's not possible to use Cursor, and stats areg easier to use
     * than CursorImpl. This relies on the fact that the stats actually
     * correctly account for deleted entries.
     */
    private void countLNs(int expectNameLNs,
                          int expectMapLNs)
            throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);

        /* check number of LNs in the id mapping tree. */
        DatabaseImpl mapDbImpl =
            envImpl.getDbTree().getDb(DbTree.ID_DB_ID);
        // mapDbImpl.getTree().dump();
        BtreeStats mapStats =
            (BtreeStats) mapDbImpl.stat(new StatsConfig());
        assertEquals(expectMapLNs,
                     (mapStats.getLeafNodeCount()));

        /* check number of LNs in the naming tree. */
        DatabaseImpl nameDbImpl =
            envImpl.getDbTree().getDb(DbTree.NAME_DB_ID);
        BtreeStats nameStats =
            (BtreeStats) nameDbImpl.stat(new StatsConfig());
        assertEquals(expectNameLNs,
                     (nameStats.getLeafNodeCount()));
    }
}
