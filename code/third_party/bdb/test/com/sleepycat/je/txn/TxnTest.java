/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TxnTest.java,v 1.97 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.txn;

import static com.sleepycat.je.txn.LockStatDefinition.LOCK_READ_LOCKS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_WRITE_LOCKS;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.LockNotAvailableException;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.OperationFailureException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.ReplicationContext;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.util.DualTestCase;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.StatGroup;

/*
 * Simple transaction testing
 */
public class TxnTest extends DualTestCase {
    private final File envHome;
    private Environment env;
    private Database db;

    public TxnTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws Exception {

        super.setUp();

        /* Ignore the node equality check in replicated test. */
        if (isReplicatedTest(getClass())) {
            resetNodeEqualityCheck();
        }

        IN.ACCUMULATED_LIMIT = 0;
        Txn.ACCUMULATED_LIMIT = 0;
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    private void createEnv() {
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     "6");
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env = create(envHome, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            db = env.openDatabase(null, "foo", dbConfig);
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void tearDown()
        throws Exception {

        super.tearDown();
    }

    private void closeEnv() {
        try {
            if (db != null) {
                db.close();
            }
            db = null;
            if (env != null) {
                close(env);
            }
            env = null;
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }

    /**
     * Test transaction locking and releasing.
     */
    public void testBasicLocking()
        throws Throwable {

        createEnv();

        try {
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            LN ln = new LN(new byte[0], envImpl, false);

            /*
             * Make a null txn that will lock. Take a lock and then end the
             * operation.
             */
            MemoryBudget mb = envImpl.getMemoryBudget();

            long beforeLock = mb.getCacheMemoryUsage();
            Locker nullTxn = BasicLocker.createBasicLocker(envImpl);

            LockGrantType lockGrant = nullTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            long afterLock = mb.getCacheMemoryUsage();
            checkHeldLocks(nullTxn, 1, 0);

            nullTxn.releaseNonTxnLocks();
            long afterRelease = mb.getCacheMemoryUsage();
            checkHeldLocks(nullTxn, 0, 0);
            checkCacheUsage(beforeLock, afterLock, afterRelease,
                            LockManager.TOTAL_THINLOCKIMPL_OVERHEAD);

            /* Take a lock, release it. */
            beforeLock = mb.getCacheMemoryUsage();
            lockGrant = nullTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            afterLock = mb.getCacheMemoryUsage();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(nullTxn, 1, 0);

            nullTxn.releaseLock(ln.getNodeId());
            checkHeldLocks(nullTxn, 0, 0);
            afterRelease = mb.getCacheMemoryUsage();
            checkCacheUsage(beforeLock, afterLock, afterRelease,
                            LockManager.TOTAL_THINLOCKIMPL_OVERHEAD);

            /*
             * Make a user transaction, check lock and release.
             */
            beforeLock = mb.getCacheMemoryUsage();

            /* Use a Master replication context in a replicated test. */
            ReplicationContext context = null;
            if (isReplicatedTest(getClass())) {
                context = ReplicationContext.MASTER;
            } else {
                context = ReplicationContext.NO_REPLICATE;
            }
            Txn userTxn = Txn.createLocalTxn(envImpl, new TransactionConfig());

            lockGrant = userTxn.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            afterLock = mb.getCacheMemoryUsage();

            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 1, 0);

            /* Try demoting, nothing should happen. */
            try {
                userTxn.demoteLock(ln.getNodeId());
                fail("exception not thrown on phoney demoteLock");
            } catch (AssertionError e){
            }
            checkHeldLocks(userTxn, 1, 0);
            long afterDemotion = mb.getCacheMemoryUsage();
            assertEquals(afterLock, afterDemotion);

            /* Make it a write lock, then demote. */
            lockGrant = userTxn.lock
                (ln.getNodeId(), LockType.WRITE, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.PROMOTION, lockGrant);
            long afterWriteLock = mb.getCacheMemoryUsage();
            assertTrue(afterWriteLock > afterLock);
            assertTrue(afterLock > beforeLock);

            checkHeldLocks(userTxn, 0, 1);
            userTxn.demoteLock(ln.getNodeId());
            checkHeldLocks(userTxn, 1, 0);

            /* Shouldn't release at operation end. */
            userTxn.operationEnd();
            checkHeldLocks(userTxn, 1, 0);

            userTxn.releaseLock(ln.getNodeId());
            checkHeldLocks(userTxn, 0, 0);
            userTxn.commit(Durability.COMMIT_SYNC);
            afterRelease = mb.getCacheMemoryUsage();
            assertTrue(afterLock > beforeLock);

            closeEnv();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test lock mutation.
     */
    public void testLockMutation()
        throws Throwable {

        createEnv();

        try {

            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            LN ln = new LN(new byte[0], envImpl, false);

            MemoryBudget mb = envImpl.getMemoryBudget();

            long beforeLock = mb.getCacheMemoryUsage();
            Txn userTxn1 = Txn.createUserTxn(envImpl, new TransactionConfig());
            Txn userTxn2 = Txn.createUserTxn(envImpl, new TransactionConfig());

            EnvironmentStats envStats = env.getStats(null);
            assertEquals(1, envStats.getNTotalLocks());
            LockGrantType lockGrant1 = userTxn1.lock
                (ln.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant1);
            checkHeldLocks(userTxn1, 1, 0);
            envStats = env.getStats(null);
            assertEquals(2, envStats.getNTotalLocks());

            try {
                userTxn2.lock(ln.getNodeId(), LockType.WRITE, false,
                              DbInternal.getDatabaseImpl(db)).getLockGrant();
            } catch (LockConflictException DE) {
                // ok
            }
            envStats = env.getStats(null);
            assertEquals(2, envStats.getNTotalLocks());
            checkHeldLocks(userTxn2, 0, 0);

            userTxn1.commit();
            userTxn2.abort(false);

            /*
             * Since the replicated tests use shared cache to reduce the
             * memory usage, so this check would fail. Ignore it in replicated
             * tests.
             */
            long afterRelease = mb.getCacheMemoryUsage();
            if (!isReplicatedTest(getClass())) {
                assertEquals(beforeLock, afterRelease);
            }

            closeEnv();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    private void checkHeldLocks(Locker txn,
                                int numReadLocks,
                                int numWriteLocks)
        throws DatabaseException {

        StatGroup stat = txn.collectStats();
        assertEquals(numReadLocks, stat.getInt(LOCK_READ_LOCKS));
        assertEquals(numWriteLocks, stat.getInt(LOCK_WRITE_LOCKS));
    }

    /**
     * Test transaction commit, from the locking point of view.
     */
    public void testCommit()
        throws Throwable {

        createEnv();

        try {
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            LN ln1 = new LN(new byte[0], envImpl, false);
            LN ln2 = new LN(new byte[0], envImpl, false);

            /* Use a Master replication context in a replicated test. */
            ReplicationContext context = null;
            if (isReplicatedTest(getClass())) {
                context = ReplicationContext.MASTER;
            } else {
                context = ReplicationContext.NO_REPLICATE;
            }
            Txn userTxn = Txn.createUserTxn(envImpl, new TransactionConfig());

            /* Get read lock 1. */
            LockGrantType lockGrant = userTxn.lock
                (ln1.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 1, 0);

            /* Get read lock 2. */
            lockGrant = userTxn.lock
                (ln2.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.NEW, lockGrant);
            checkHeldLocks(userTxn, 2, 0);

            /* Upgrade read lock 2 to a write. */
            lockGrant = userTxn.lock
                (ln2.getNodeId(), LockType.WRITE, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.PROMOTION, lockGrant);
            checkHeldLocks(userTxn, 1, 1);

            /* Read lock 1 again, shouldn't increase count. */
            lockGrant = userTxn.lock
                (ln1.getNodeId(), LockType.READ, false,
                 DbInternal.getDatabaseImpl(db)).
                getLockGrant();
            assertEquals(LockGrantType.EXISTING, lockGrant);
            checkHeldLocks(userTxn, 1, 1);

            /*
             * The commit won't actually write a log record if this
             * transaction has never done an update, so fake it out and simulate
             * a write.
             */
            userTxn.addLogInfo(DbLsn.makeLsn(1, 1000));
            long commitLsn = userTxn.commit(Durability.COMMIT_SYNC);
            checkHeldLocks(userTxn, 0, 0);

            TxnCommit commitRecord =
                (TxnCommit) envImpl.getLogManager().getEntry(commitLsn);

            assertEquals(userTxn.getId(), commitRecord.getId());
            assertEquals(userTxn.getLastLsn(), commitRecord.getLastLsn());

            closeEnv();
        } catch (Throwable t) {
            /* Print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Make sure an abort never tries to split the tree.
     */
    public void testAbortNoSplit()
        throws Throwable {

        createEnv();

        try {
            Transaction txn = env.beginTransaction(null, null);

            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            dataDbt.setData(new byte[1]);

            /* Insert enough data so that the tree is ripe for a split. */
            int numForSplit = 25;
            for (int i = 0; i < numForSplit; i++) {
                keyDbt.setData(TestUtils.getTestArray(i));
                db.put(txn, keyDbt, dataDbt);
            }

            /* Check that we're ready for a split. */
            DatabaseImpl database = DbInternal.getDatabaseImpl(db);
            CheckReadyToSplit splitChecker = new CheckReadyToSplit(database);
            database.getTree().withRootLatchedShared(splitChecker);
            assertTrue(splitChecker.getReadyToSplit());

            /*
             * Make another txn that will get a read lock on the map
             * LSN. Then abort the first txn. It shouldn't try to do a
             * split, if it does, we'll run into the
             * no-latches-while-locking check.
             */
            Transaction txnSpoiler = env.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            Database dbSpoiler = env.openDatabase(txnSpoiler, "foo", dbConfig);

            txn.abort();

            /*
             * The database should be empty
             */
            Cursor cursor = dbSpoiler.openCursor(txnSpoiler, null);

            assertTrue(cursor.getFirst(keyDbt, dataDbt, LockMode.DEFAULT) !=
                       OperationStatus.SUCCESS);
            cursor.close();
            txnSpoiler.abort();

            closeEnv();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    public void testTransactionName()
        throws Throwable {

        createEnv();

        try {
            Transaction txn = env.beginTransaction(null, null);
            txn.setName("blort");
            assertEquals("blort", txn.getName());
            txn.abort();

            /*
             * [#14349] Make sure the txn is printable after closing. We
             * once had a NullPointerException.
             */
            txn.toString();

            closeEnv();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test all combinations of sync, nosync, and writeNoSync for txn
     * commits.
     */

    /* SyncCombo expresses all the combinations of txn sync properties. */
    private static class SyncCombo {
        private final boolean envNoSync;
        private final boolean envWriteNoSync;
        private final boolean txnNoSync;
        private final boolean txnWriteNoSync;
        private final boolean txnSync;
        boolean expectSync;
        boolean expectWrite;
        boolean expectException;

        SyncCombo(int envWriteNoSync,
                  int envNoSync,
                  int txnSync,
                  int txnWriteNoSync,
                  int txnNoSync,
                  boolean expectSync,
                  boolean expectWrite,
                  boolean expectException) {
            this.envNoSync = (envNoSync == 0) ? false : true;
            this.envWriteNoSync = (envWriteNoSync == 0) ? false : true;
            this.txnNoSync = (txnNoSync == 0) ? false : true;
            this.txnWriteNoSync = (txnWriteNoSync == 0) ? false : true;
            this.txnSync = (txnSync == 0) ? false : true;
            this.expectSync = expectSync;
            this.expectWrite = expectWrite;
            this.expectException = expectException;
        }

        TransactionConfig getTxnConfig() {
            TransactionConfig txnConfig = new TransactionConfig();
            txnConfig.setSync(txnSync);
            txnConfig.setWriteNoSync(txnWriteNoSync);
            txnConfig.setNoSync(txnNoSync);
            return txnConfig;
        }

        void setEnvironmentMutableConfig(Environment env)
            throws DatabaseException {

            EnvironmentMutableConfig config = env.getMutableConfig();
            config.setTxnNoSync(envNoSync);
            config.setTxnWriteNoSync(envWriteNoSync);
            env.setMutableConfig(config);
        }
    }

    public void testSyncCombo()
        throws Throwable {

        createEnv();

        RandomAccessFile logFile =
            new RandomAccessFile(new File(env.getHome(), "00000000.jdb"), "r");
        try {
            SyncCombo[] testCombinations = {
        /*            Env    Env    Txn    Txn    Txn    Expect Expect Expect
         *            WrNoSy NoSy   Sync  WrNoSy  NoSyc  Sync   Write   IAE*/
        new SyncCombo(  0,     0,     0,     0,     0,    true,  true, false),
        new SyncCombo(  0,     0,     0,     0,     1,   false, false, false),
        new SyncCombo(  0,     0,     0,     1,     0,   false,  true, false),
        new SyncCombo(  0,     0,     0,     1,     1,   false,  true, true),
        new SyncCombo(  0,     0,     1,     0,     0,    true,  true, false),
        new SyncCombo(  0,     0,     1,     0,     1,    true,  true, true),
        new SyncCombo(  0,     0,     1,     1,     0,    true,  true, true),
        new SyncCombo(  0,     0,     1,     1,     1,    true,  true, true),
        new SyncCombo(  0,     1,     0,     0,     0,   false, false, false),
        new SyncCombo(  0,     1,     0,     0,     1,   false, false, false),
        new SyncCombo(  0,     1,     0,     1,     0,   false,  true, false),
        new SyncCombo(  0,     1,     0,     1,     1,   false,  true, true),
        new SyncCombo(  0,     1,     1,     0,     0,    true,  true, false),
        new SyncCombo(  0,     1,     1,     0,     1,    true,  true, true),
        new SyncCombo(  0,     1,     1,     1,     0,    true,  true, true),
        new SyncCombo(  0,     1,     1,     1,     1,    true,  true, true),
        new SyncCombo(  1,     0,     0,     0,     0,   false,  true, false),
        new SyncCombo(  1,     0,     0,     0,     1,   false, false, false),
        new SyncCombo(  1,     0,     0,     1,     0,   false,  true, false),
        new SyncCombo(  1,     0,     0,     1,     1,   false,  true, true),
        new SyncCombo(  1,     0,     1,     0,     0,    true,  true, false),
        new SyncCombo(  1,     0,     1,     0,     1,    true,  true, true),
        new SyncCombo(  1,     0,     1,     1,     0,    true,  true, true),
        new SyncCombo(  1,     0,     1,     1,     1,    true,  true, true),
        new SyncCombo(  1,     1,     0,     0,     0,   false,  true, true),
        new SyncCombo(  1,     1,     0,     0,     1,   false, false, true),
        new SyncCombo(  1,     1,     0,     1,     0,   false,  true, true),
        new SyncCombo(  1,     1,     0,     1,     1,   false,  true, true),
        new SyncCombo(  1,     1,     1,     0,     0,    true,  true, true),
        new SyncCombo(  1,     1,     1,     0,     1,    true,  true, true),
        new SyncCombo(  1,     1,     1,     1,     0,    true,  true, true),
        new SyncCombo(  1,     1,     1,     1,     1,    true,  true, true)
            };

            /* envNoSync=false with default env config */
            assertTrue(!env.getMutableConfig().getTxnNoSync());

            /* envWriteNoSync=false with default env config */
            assertTrue(!env.getMutableConfig().getTxnWriteNoSync());

            /*
             * For each combination of settings, call commit and
             * check that we have the expected sync and log
             * write. Make sure that commitSync(), commitNoSync always
             * override all preferences.
             */
            for (int i = 0; i < testCombinations.length; i++) {
                SyncCombo combo = testCombinations[i];
                boolean IAECaught = false;
                try {
                    TransactionConfig txnConfig = combo.getTxnConfig();
                    combo.setEnvironmentMutableConfig(env);
                    syncExplicit(logFile, txnConfig,
                                 combo.expectSync, combo.expectWrite);
                } catch (IllegalArgumentException IAE) {
                    IAECaught = true;
                }
                assertTrue(IAECaught == combo.expectException);
            }

            SyncCombo[] autoCommitCombinations = {
        /*            Env    Env    Txn    Txn    Txn    Expect Expect Expect
         *            WrNoSy NoSy   Sync  WrNoSy  NoSyc  Sync   Write   IAE*/
        new SyncCombo(  0,     0,     0,     0,     0,    true,  true, false),
        new SyncCombo(  0,     1,     0,     0,     0,   false, false, false),
        new SyncCombo(  1,     0,     0,     0,     0,   false,  true, false),
        new SyncCombo(  1,     1,     0,     0,     0,   false,  true, true)
            };

            for (int i = 0; i < autoCommitCombinations.length; i++) {
                SyncCombo combo = autoCommitCombinations[i];
                boolean IAECaught = false;
                try {
                    combo.setEnvironmentMutableConfig(env);
                } catch (IllegalArgumentException IAE) {
                    IAECaught = true;
                }
                assertTrue(IAECaught == combo.expectException);
                syncAutoCommit(logFile, combo.expectSync, combo.expectWrite);
            }
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        } finally {
            logFile.close();

            closeEnv();
        }
    }

    enum DurabilityAPI {SYNC_API, DUR_API, DEFAULT_API};

    /*
     * Returns true if there is mixed mode usage across the two apis
     */
    private boolean mixedModeUsage(DurabilityAPI outerAPI,
                                   DurabilityAPI innerAPI) {
        if ((innerAPI == DurabilityAPI.DEFAULT_API) ||
             (outerAPI == DurabilityAPI.DEFAULT_API)){
            return false;
        }

        if (innerAPI == outerAPI) {
            return false;
        }
        /* Mix of sync and durability APIs */
        return true;
    }

    /*
     * Does a three level check at the env, config and transaction levels to
     * check for mixed mode uaage
     */
    boolean mixedModeUsage(DurabilityAPI envAPI,
                           DurabilityAPI tconfigAPI,
                           DurabilityAPI transAPI) {
        DurabilityAPI outerAPI;
        if (tconfigAPI == DurabilityAPI.DEFAULT_API) {
            outerAPI = envAPI;
        } else {
            outerAPI = tconfigAPI;
        }
        return mixedModeUsage(outerAPI, transAPI);
    }

    /*
     * Test local mixed mode operations on MutableConfig and TransactionConfig
     */
    public void testOneLevelDurabilityComboErrors() {
        createEnv();

        EnvironmentMutableConfig config = new EnvironmentMutableConfig();
        config.setTxnNoSync(true);
        try {
            config.setDurability(Durability.COMMIT_NO_SYNC);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(true); // pass expected exception
        }
        config =  new EnvironmentMutableConfig();
        config.setDurability(Durability.COMMIT_NO_SYNC);
        try {
            config.setTxnNoSync(true);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(true); // pass expected exception
        }

        TransactionConfig txnConfig = new TransactionConfig();
        txnConfig.setNoSync(true);
        try {
            txnConfig.setDurability(Durability.COMMIT_NO_SYNC);
        } catch (IllegalArgumentException e) {
            assertTrue(true); // pass expected exception
        }

        txnConfig = new TransactionConfig();
        txnConfig.setDurability(Durability.COMMIT_NO_SYNC);
        try {
        txnConfig.setNoSync(true);
        } catch (IllegalArgumentException e) {
            assertTrue(true); // pass expected exception
        }

        closeEnv();
    }

    /*
     * Test for exceptions resulting from mixed mode usage.
     */
    public void testMultiLevelLocalDurabilityComboErrors()
        throws Throwable {

        createEnv();

        for (DurabilityAPI envAPI: DurabilityAPI.values()) {
            EnvironmentMutableConfig config =  new EnvironmentMutableConfig();
            switch (envAPI) {
                case SYNC_API:
                    config.setTxnNoSync(true);
                    break;
                case DUR_API:
                    config.setDurability(Durability.COMMIT_NO_SYNC);
                    break;
                case DEFAULT_API:
                    break;
            }
            env.setMutableConfig(config);
            for (DurabilityAPI tconfigAPI: DurabilityAPI.values()) {
                TransactionConfig txnConfig = new TransactionConfig();
                switch (tconfigAPI) {
                    case SYNC_API:
                        txnConfig.setNoSync(true);
                        break;
                    case DUR_API:
                        txnConfig.setDurability(Durability.COMMIT_NO_SYNC);
                        break;
                    case DEFAULT_API:
                        txnConfig = null;
                        break;
                    }
                try {
                    Transaction txn = env.beginTransaction(null, txnConfig);
                    txn.abort();
                    assertFalse(mixedModeUsage(envAPI,tconfigAPI));
                    for (DurabilityAPI transAPI : DurabilityAPI.values()) {
                        Transaction t = env.beginTransaction(null, txnConfig);
                        try {
                            switch (transAPI) {
                                case SYNC_API:
                                    t.commitNoSync();
                                    break;
                                case DUR_API:
                                    t.commit(Durability.COMMIT_NO_SYNC);
                                    break;
                                case DEFAULT_API:
                                    t.commit();
                                    break;
                            }
                            assertFalse(mixedModeUsage(envAPI,
                                                       tconfigAPI,
                                                       transAPI));
                        } catch (IllegalArgumentException e) {
                            t.abort();
                            assertTrue(mixedModeUsage(envAPI,
                                                      tconfigAPI,
                                                      transAPI));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    assertTrue(mixedModeUsage(envAPI,tconfigAPI));
                }
            }
        }
        closeEnv();
    }

    public void testLocalDurabilityCombo()
        throws Throwable {

        createEnv();

        RandomAccessFile logFile =
            new RandomAccessFile(new File(env.getHome(), "00000000.jdb"), "r");
        /* Note that the default must be first. An "unspecified"  durability is
         * represented by a null value in the env props. It's not possible to
         * restore durability back to its "unspecified" state, since
         * Environment.setMutableConfig effectively does a merge operation.
         */
        Durability[] localDurabilities = new Durability[] {
                    null, /* Run default settings first.  */
                    Durability.COMMIT_SYNC,
                    Durability.COMMIT_WRITE_NO_SYNC,
                    Durability.COMMIT_NO_SYNC
                    };

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);

        try {
            for (Durability envDurability : localDurabilities) {
                EnvironmentMutableConfig config =  env.getMutableConfig();
                config.setDurability(envDurability);
                env.setMutableConfig(config);
                for (Durability transConfigDurability : localDurabilities) {
                    TransactionConfig txnConfig = null;
                    if (transConfigDurability != null) {
                        txnConfig = new TransactionConfig();
                        txnConfig.setDurability(transConfigDurability);
                    }
                    for (Durability transDurability : localDurabilities) {
                        long beforeSyncs = getNSyncs();
                        Transaction txn = env.beginTransaction(null, txnConfig);
                        db.put(txn, key, data);
                        long beforeLength = logFile.length();
                        if (transDurability == null) {
                            txn.commit();
                        } else {
                            txn.commit(transDurability);
                        }
                        Durability effectiveDurability =
                            (transDurability != null) ?
                            transDurability :
                            ((transConfigDurability != null) ?
                             transConfigDurability :
                             ((envDurability != null) ?
                              envDurability :
                              Durability.COMMIT_SYNC));

                        long afterSyncs = getNSyncs();
                        long afterLength = logFile.length();
                        boolean syncOccurred = afterSyncs > beforeSyncs;
                        boolean writeOccurred = afterLength > beforeLength;
                        switch (effectiveDurability.getLocalSync()) {
                            case SYNC:
                                assertTrue(syncOccurred);
                                assertTrue(writeOccurred);
                                break;
                            case NO_SYNC:
                                if (syncOccurred) {
                                    assertFalse(syncOccurred);
                                }
                                assertFalse(writeOccurred);
                                break;
                            case WRITE_NO_SYNC:
                                assertFalse(syncOccurred);
                                assertTrue(writeOccurred);
                                break;
                        }
                    }
                }
            }
        } finally {
            logFile.close();

            closeEnv();
        }
    }

    /**
     * Does an explicit commit and returns whether an fsync occured.
     */
    private void syncExplicit(RandomAccessFile lastLogFile,
                              TransactionConfig config,
                              boolean expectSync,
                              boolean expectWrite)
        throws DatabaseException, IOException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);

        long beforeSyncs = getNSyncs();
        Transaction txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        long beforeLength = lastLogFile.length();
        txn.commit();
        long afterSyncs = getNSyncs();
        long afterLength = lastLogFile.length();
        boolean syncOccurred = afterSyncs > beforeSyncs;
        boolean writeOccurred = afterLength > beforeLength;
        assertEquals(expectSync, syncOccurred);
        assertEquals(expectWrite, writeOccurred);

        /*
         * Make sure explicit sync/noSync/writeNoSync always works.
         */

        /* Expect a sync and write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs > beforeSyncs);
        assert(afterLength > beforeLength);

        /* Expect neither a sync nor write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitNoSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs == beforeSyncs);
        assert(afterLength == beforeLength);

        /* Expect no sync but do expect a write. */
        beforeSyncs = getNSyncs();
        beforeLength = lastLogFile.length();
        txn = env.beginTransaction(null, config);
        db.put(txn, key, data);
        txn.commitWriteNoSync();
        afterSyncs = getNSyncs();
        afterLength = lastLogFile.length();
        assert(afterSyncs == beforeSyncs);
        assert(afterLength > beforeLength);
    }

    /**
     * Does an auto-commit and returns whether an fsync occured.
     */
    private void syncAutoCommit(RandomAccessFile lastLogFile,
                                boolean expectSync,
                                boolean expectWrite)
        throws DatabaseException, IOException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);
        long beforeSyncs = getNSyncs();
        long beforeLength = lastLogFile.length();
        db.put(null, key, data);
        long afterLength = lastLogFile.length();
        long afterSyncs = getNSyncs();
        boolean syncOccurred = afterSyncs > beforeSyncs;
        assertEquals(expectSync, syncOccurred);
        assertEquals(expectWrite, (afterLength > beforeLength));
    }

    /**
     * Returns number of fsyncs statistic.
     */
    private long getNSyncs() {
        return DbInternal.getEnvironmentImpl(env)
                         .getFileManager()
                         .getNFSyncs();
    }

    public void testNoWaitConfig()
        throws Throwable {

        createEnv();

        try {
            TransactionConfig defaultConfig = new TransactionConfig();
            TransactionConfig noWaitConfig = new TransactionConfig();
            noWaitConfig.setNoWait(true);
            Transaction txn;

            /* noWait=false */

            assertTrue(!isNoWaitTxn(null));

            txn = env.beginTransaction(null, null);
            assertTrue(!isNoWaitTxn(txn));
            txn.abort();

            txn = env.beginTransaction(null, defaultConfig);
            assertTrue(!isNoWaitTxn(txn));
            txn.abort();

            /* noWait=true */

            txn = env.beginTransaction(null, noWaitConfig);
            assertTrue(isNoWaitTxn(txn));
            txn.abort();

            closeEnv();
        } catch (Throwable t) {
            /* print stack trace before going to teardown. */
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Returns whether the given txn is a no-wait txn, or if the txn parameter
     * is null returns whether an auto-commit txn is a no-wait txn.
     */
    private boolean isNoWaitTxn(Transaction txn)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[1]);

        /* Use a wait txn to get a write lock. */
        Transaction txn2 = env.beginTransaction(null, null);
        db.put(txn2, key, data);

        try {
            db.put(txn, key, data);
            throw new IllegalStateException
                ("Lock should not have been granted");
        } catch (LockNotAvailableException e) {
            assertEquals
                ("false", env.getConfig().getConfigParam
                            (EnvironmentConfig.LOCK_OLD_LOCK_EXCEPTIONS));
            return true;
        } catch (LockNotGrantedException e) {
            assertEquals
                ("true", env.getConfig().getConfigParam
                            (EnvironmentConfig.LOCK_OLD_LOCK_EXCEPTIONS));
            return true;
        } catch (LockConflictException e) {
            return false;
        } finally {
            txn2.abort();
        }
    }

    /*
     * Assert that cache utilization is correctly incremented by locks and
     * txns, and decremented after release.
     */
    private void checkCacheUsage(long beforeLock,
                                 long afterLock,
                                 long afterRelease,
                                 long expectedSize) {
        assertEquals(beforeLock, afterRelease);
        assertEquals(afterLock, (beforeLock + expectedSize));
    }

    class CheckReadyToSplit implements WithRootLatched {
        private boolean readyToSplit;
        private final DatabaseImpl database;

        CheckReadyToSplit(DatabaseImpl database) {
            readyToSplit = false;
            this.database = database;
        }

        public boolean getReadyToSplit() {
            return readyToSplit;
        }

        public IN doWork(ChildReference root)
            throws DatabaseException {

            IN rootIN = (IN) root.fetchTarget(database, null);
            readyToSplit = rootIN.needsSplitting();
            return null;
        }
    }

    /**
     * Ensures that when an operation failure sets a txn to abort-only, the
     * same exeption is rethrown if the user insists on continuing to use the
     * txn.
     */
    public void testRepeatingOperationFailures() {
        createEnv();

        final Transaction txn1 = env.beginTransaction(null, null);
        txn1.setLockTimeout(0);
        final Transaction txn2 = env.beginTransaction(null, null);

        final DatabaseEntry key1 = new DatabaseEntry(new byte[] {1});
        final DatabaseEntry key2 = new DatabaseEntry(new byte[] {2});
        final DatabaseEntry data = new DatabaseEntry(new byte[1]);

        db.put(txn1, key1, data);
        OperationFailureException expected = null;
        try {
            db.put(txn2, key1, data);
            fail();
        } catch (OperationFailureException e) {
            expected = e;
        }
        assertTrue(!txn2.isValid());
        try {
            db.put(txn2, key2, data);
            fail();
        } catch (OperationFailureException e) {
            assertSame(expected, e.getCause());
        }

        txn1.abort();
        txn2.abort();

        closeEnv();
    }
}
