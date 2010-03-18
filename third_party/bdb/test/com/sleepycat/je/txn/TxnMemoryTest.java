/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TxnMemoryTest.java,v 1.26 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.txn;

import java.io.File;
import java.util.Enumeration;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.DualTestCase;
import com.sleepycat.je.util.TestUtils;

public class TxnMemoryTest extends DualTestCase {
    private static final boolean DEBUG = false;
    private static final String DB_NAME = "foo";

    private static final String LOCK_AUTOTXN = "lock-autotxn";
    private static final String LOCK_USERTXN  = "lock-usertxn";
    private static final String LOCK_NOTXN  = "lock-notxn";
    private static final String COMMIT = "commit";
    private static final String ABORT = "abort";
    private static final String[] END_MODE = {COMMIT, ABORT};

    private final File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private MemoryBudget mb;
    private Database db;
    private final DatabaseEntry keyEntry = new DatabaseEntry();
    private final DatabaseEntry dataEntry = new DatabaseEntry();
    private String lockMode;
    private String endMode;

    private long beforeAction;
    private long afterTxnsCreated;
    private long afterAction;
    private Transaction[] txns;

    private final int numTxns = 2;
    private final int numRecordsPerTxn = 30;

    static protected Class<?> testClass = TxnMemoryTest.class;

    public static Test suite() {
        TestSuite allTests = new TestSuite();
        String[] testModes = null;

        /* 
         * Remove the non-txn configuration for environment, so that
         * we can run this test.
         */
        if (isReplicatedTest(testClass)) {
            testModes = new String[] {LOCK_USERTXN};
        } else {
            testModes = new String[] {LOCK_AUTOTXN, LOCK_USERTXN, LOCK_NOTXN};
        }

        for (int i = 0; i < testModes.length; i += 1) {
            for (int eMode = 0; eMode < END_MODE.length; eMode ++) {
                TestSuite suite = new TestSuite(testClass);
                Enumeration e = suite.tests();
                while (e.hasMoreElements()) {
                    TxnMemoryTest test = (TxnMemoryTest) e.nextElement();
                    test.init(testModes[i], END_MODE[eMode]);
                    allTests.addTest(test);
                }
            }
        }
        return allTests;
    }

    public TxnMemoryTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    private void init(String lockMode, String endMode) {
        this.lockMode = lockMode;
        this.endMode = endMode;
    }

    @Override
    public void setUp() 
        throws Exception {

        super.setUp();

        IN.ACCUMULATED_LIMIT = 0;
        Txn.ACCUMULATED_LIMIT = 0;

        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() 
        throws Exception {

        /* Set test name for reporting; cannot be done in the ctor or setUp. */
        setName(lockMode + '/' + endMode + ":" + getName());

        super.tearDown();
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
    }

    /**
     * Opens the environment and database.
     */
    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig config = TestUtils.initEnvConfig();

        /*
         * ReadCommitted isolation is not allowed by this test because we
         * expect no locks/memory to be freed when using a transaction.
         */
        DbInternal.setTxnReadCommitted(config, false);

        /* Cleaner detail tracking adds to the memory budget; disable it. */
        config.setConfigParam
            (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");

        config.setTransactional(true);
        config.setAllowCreate(true);
        env = create(envHome, config);
        envImpl = DbInternal.getEnvironmentImpl(env);
        mb = envImpl.getMemoryBudget();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(!lockMode.equals(LOCK_NOTXN));
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, DB_NAME, dbConfig);
    }

    /**
     * Closes the environment and database.
     */
    private void closeEnv(boolean doCheckpoint)
        throws DatabaseException {

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            close(env);
            env = null;
        }
    }

    /**
     * Insert and then update some records. Measure memory usage at different
     * points in this sequence, asserting that the memory usage count is
     * properly decremented.
     */
    public void testWriteLocks()
        throws DatabaseException {

        loadData();

        /*
         * Now update the database transactionally. This should not change
         * the node related memory, but should add txn related cache
         * consumption. If this is a user transaction, we should
         * hold locks and consume more memory.
         */
        for (int t = 0; t < numTxns; t++) {
            for (int i = 0; i < numRecordsPerTxn; i++) {
                int value = i + (t*numRecordsPerTxn);
                IntegerBinding.intToEntry(value, keyEntry);
                IntegerBinding.intToEntry(value+1, dataEntry);
                assertEquals(db.put(txns[t], keyEntry, dataEntry),
                             OperationStatus.SUCCESS);
            }
        }
        afterAction = mb.getLockMemoryUsage();

        closeTxns(true);
    }

    /**
     * Insert and then scan some records. Measure memory usage at different
     * points in this sequence, asserting that the memory usage count is
     * properly decremented.
     */
    public void testReadLocks()
        throws DatabaseException {

        loadData();

        /*
         * Now scan the database. Make sure all locking overhead is
         * released.
         */
        for (int t = 0; t < numTxns; t++) {
            Cursor c = db.openCursor(txns[t], null);
            while (c.getNext(keyEntry, dataEntry, null) ==
                   OperationStatus.SUCCESS) {
            }
            c.close();
        }
        afterAction = mb.getLockMemoryUsage();

        closeTxns(false);
    }

    private void loadData()
        throws DatabaseException {

        openEnv();

        /* Build up a database to establish a given cache size. */
        for (int t = 0; t < numTxns; t++) {
            for (int i = 0; i < numRecordsPerTxn; i++) {

                int value = i + (t*numRecordsPerTxn);
                IntegerBinding.intToEntry(value, keyEntry);
                IntegerBinding.intToEntry(value, dataEntry);
                assertEquals(db.put(null, keyEntry, dataEntry),
                             OperationStatus.SUCCESS);
            }
        }

        beforeAction = mb.getLockMemoryUsage();

        /* Make some transactions. */
        txns = new Transaction[numTxns];
        if (lockMode.equals(LOCK_USERTXN)) {
            for (int t = 0; t < numTxns; t++) {
                txns[t] = env.beginTransaction(null, null);
            }

            afterTxnsCreated = mb.getLockMemoryUsage();
            assertTrue( "afterTxns=" + afterTxnsCreated +
                        "beforeUpdate=" + beforeAction,
                        (afterTxnsCreated > beforeAction));
        }
    }

    private void closeTxns(boolean writesDone)
        throws DatabaseException {

        assertTrue(afterAction > afterTxnsCreated);

        /*
         * If this is not a user transactional lock, we should be done
         * with all locking overhead. If it is a user transaction, we
         * only release memory after locks are released at commit or
         * abort.
         */
        if (lockMode.equals(LOCK_USERTXN)) {

            /*
             * Note: expectedLockUsage is annoyingly fragile. If we change
             * the lock implementation, this may not be the right number
             * to check.
             */
            long expectedLockUsage =
                   (numRecordsPerTxn * numTxns *
                    MemoryBudget.THINLOCKIMPL_OVERHEAD);

            assertTrue((afterAction - afterTxnsCreated) >= expectedLockUsage);

            for (int t = 0; t < numTxns; t++) {
                Transaction txn = txns[t];
                if (endMode.equals(COMMIT)) {
                    txn.commit();
                } else {
                    txn.abort();
                }
            }

            long afterTxnEnd = mb.getLockMemoryUsage();

            assertTrue("lockMode=" + lockMode +
                       " endMode=" + endMode +
                       " afterTxnEnd=" + afterTxnEnd +
                       " beforeAction=" + beforeAction,
                       (afterTxnEnd <= beforeAction));
        }
        if (DEBUG) {
            System.out.println("afterUpdate = " + afterAction +
                               " before=" + beforeAction);
        }

        closeEnv(true);
    }
}
