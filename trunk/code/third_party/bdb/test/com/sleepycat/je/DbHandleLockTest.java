/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DbHandleLockTest.java,v 1.31 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import com.sleepycat.je.util.DualTestCase;
import com.sleepycat.je.util.TestUtils;

/**
 * BDB's transactional DDL operations (database creation, truncation,
 * remove and rename) need special support through what we call "handle" locks.
 *
 * When a database is created, a write lock is taken. When the creation 
 * transaction is committed, that write lock should be turned into a read lock
 * and should be transferred to the database handle.
 *
 * Note that when this test is run in HA mode, environment creation results in
 * a different number of outstanding locks. And example of a HA specific lock
 * is that taken for the RepGroupDb, which holds replication group information.
 * Because of that, this test takes care to check for a relative number of 
 * locks, rather than an absolute number.
 */
public class DbHandleLockTest extends DualTestCase {
    private File envHome;
    private Environment env;

    public DbHandleLockTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws Exception {

        super.setUp();

        TestUtils.removeLogFiles("Setup", envHome, false);
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = create(envHome, envConfig);
    }

    @Override
    public void tearDown()
        throws Exception {

        super.tearDown();
    }

    public void testOpenHandle()
        throws Throwable {

        try {
            Transaction txnA =
                env.beginTransaction(null, TransactionConfig.DEFAULT);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
           
            LockStats oldLockStat = env.getLockStats(null);
             
            Database db = env.openDatabase(txnA, "foo", dbConfig);

            /*
             * At this point, we expect a write lock on the NameLN (the handle
             * lock).
             */
            LockStats lockStat = env.getLockStats(null);
            assertEquals(oldLockStat.getNTotalLocks() + 1, 
                    lockStat.getNTotalLocks());
            assertEquals(oldLockStat.getNWriteLocks() + 1, 
                    lockStat.getNWriteLocks());
            assertEquals(oldLockStat.getNReadLocks(), 
                    lockStat.getNReadLocks());

            txnA.commit();

            lockStat = env.getLockStats(null);
            assertEquals(oldLockStat.getNTotalLocks() + 1, 
                    lockStat.getNTotalLocks());
            assertEquals(oldLockStat.getNWriteLocks(), 
                    lockStat.getNWriteLocks());
            assertEquals(oldLockStat.getNReadLocks() + 1, 
                    lockStat.getNReadLocks());

            /* Updating the root from another txn should be possible. */
            insertData(10, db);
            db.close();

            lockStat = env.getLockStats(null);
            assertEquals(oldLockStat.getNTotalLocks(), 
                    lockStat.getNTotalLocks());
            assertEquals(oldLockStat.getNWriteLocks(), 
                    lockStat.getNWriteLocks());
            assertEquals(oldLockStat.getNReadLocks(), 
                    lockStat.getNReadLocks());
            close(env);
            env = null;
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testSR12068()
        throws Throwable {

        try {
            Transaction txnA =
                env.beginTransaction(null, TransactionConfig.DEFAULT);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database db = env.openDatabase(txnA, "foo", dbConfig);
            db.close();

            dbConfig.setExclusiveCreate(true);
            try {
                db = env.openDatabase(txnA, "foo", dbConfig);
                fail("should throw database exeception");
            } catch (DatabaseException DE) {
                /* expected Database already exists. */
            }
            dbConfig.setAllowCreate(false);
            dbConfig.setExclusiveCreate(false);
            db = env.openDatabase(txnA, "foo", dbConfig);
            db.close();
            txnA.commit();
            txnA = env.beginTransaction(null, TransactionConfig.DEFAULT);
            env.removeDatabase(txnA, "foo");
            txnA.commit();
            close(env);
            env = null;
        } catch (Throwable T) {
            T.printStackTrace();
            throw T;
        }
    }

    private void insertData(int numRecs, Database db)
        throws Throwable {

        for (int i = 0; i < numRecs; i++) {
            DatabaseEntry key = new DatabaseEntry(TestUtils.getTestArray(i));
            DatabaseEntry data = new DatabaseEntry(TestUtils.getTestArray(i));
            assertEquals(OperationStatus.SUCCESS,
                    db.put(null, key, data));
        }
    }
}
