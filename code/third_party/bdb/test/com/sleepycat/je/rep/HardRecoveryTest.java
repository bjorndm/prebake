/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: HardRecoveryTest.java,v 1.11 2010/01/11 20:01:03 linda Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CommitToken;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.rep.impl.RepImplStatDefinition;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.stream.ReplicaFeederSyncup.TestHook;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.WaitForMasterListener;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.DbTruncateLog;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.VLSN;

/**
 * Check the rollback past a commit or abort.
 */
public class HardRecoveryTest extends TestCase {
    private final boolean verbose = Boolean.getBoolean("verbose");
    private static final String DB_NAME = "testDb";
    private final File envRoot;
    private final Logger logger;

    public HardRecoveryTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
        logger = LoggerUtils.getLoggerFixedPrefix(getClass(), "Test");
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
     * HardRecovery as invoked via the ReplicatedEnvironment constructor.
     * Mimics the case where A was the master, has a commit in its log but that
     * commit has not been propagated.  A goes down, new master = B. When A
     * comes up, it has to roll back its commit and do a hard recovery. This
     * flavor of hard recovery is executed silently within the
     * ReplicatedEnvironment constructor.
     */
    public void testHardRecoveryWithConstructorNoLimit()
        throws Exception {

        doHardRecoveryWithConstructor(false);
    }

    public void testHardRecoveryWithConstructorLimit()
        throws Exception {

        doHardRecoveryWithConstructor(true);
    }

    /**
     * If setRollbackLimit == true, we expect to get a 
     * RollbackProhibitedException and to have to manually truncate the 
     * environment.
     */
    public void doHardRecoveryWithConstructor(boolean setRollbackLimit)
        throws Exception {

        RepEnvInfo[] repEnvInfo = null;
        Database db = null;
        int numNodes = 3;
        try {
            
            ReplicationConfig repConfig = null;
            if (setRollbackLimit) {
                repConfig = new ReplicationConfig();
                repConfig.setConfigParam(ReplicationConfig.TXN_ROLLBACK_LIMIT,
                                         "0");
            }

            repEnvInfo = RepTestUtils.setupEnvInfos
                (envRoot, numNodes,
                 RepTestUtils.createEnvConfig
                 (RepTestUtils.SYNC_SYNC_NONE_DURABILITY),
                 repConfig);

            ReplicatedEnvironment master = repEnvInfo[0].openEnv();
            assert master != null;

            db = createDb(master);

            logger.info("Created db on master");
            CommitToken commitToken = doInsert(master, db, 1, 1);
            CommitPointConsistencyPolicy cp =
                new CommitPointConsistencyPolicy(commitToken, 1000,
                                                 TimeUnit.SECONDS);
            for (int i = 1; i < numNodes; i++) {
                repEnvInfo[i].openEnv(cp);
            }

            /*
             * Shut down all replicas so that they don't see the next
             * commit.
             */
            for (int i = 1; i < numNodes; i++) {
                logger.info("shut down replica " +
                              repEnvInfo[i].getEnv().getNodeName());
                repEnvInfo[i].closeEnv();
            }


            /* 
             * Do work on the sole node, which is the master, then close it.
             * This work was committed, and will have to be rolled back later
             * on.  
             */
            logger.info("do master only insert");
            doInsert(master, db, 2, 5);
            checkExists(master, db, 1, 2, 3, 4, 5);
            db.close();
            db = null;
            repEnvInfo[0].closeEnv();

            /* 
             * Restart the group, make it do some other work which the
             * original master, which is down, won't see. 
             */
            logger.info("restart group");
            master = RepTestUtils.restartGroup(repEnvInfo[1], repEnvInfo[2]);

            logger.info("group came up, new master = " +  master.getNodeName());
            db = openDb(master);
            commitToken = doInsert(master, db, 10,15);
            checkNotThere(master, db, 2, 3, 4, 5);
            checkExists(master, db, 1, 10, 11, 12, 13, 14, 15);

            /* 
             * When we restart the master, it should transparently do a hard
             * recovery.
             */
            logger.info("restart old master");
            ReplicatedEnvironment oldMaster = null;
            try {
                repEnvInfo[0].openEnv
                    (new CommitPointConsistencyPolicy(commitToken, 1000,
                                                      TimeUnit.SECONDS));
                assertFalse(setRollbackLimit);
                oldMaster = repEnvInfo[0].getEnv();
                assertTrue(RepInternal.getRepImpl(oldMaster).getNodeStats().
                           getBoolean(RepImplStatDefinition.HARD_RECOVERY));
                logger.info(RepInternal.getRepImpl(oldMaster).getNodeStats().
                            getString(RepImplStatDefinition.HARD_RECOVERY_INFO));
            } catch (RollbackProhibitedException e) {

                /* 
                 * If setRollback limit is set, we should get this exception
                 * with directions on how to truncate the log. If the 
                 * limit was not set, the truncation should have been done
                 * by JE already.
                 */
                assertTrue(setRollbackLimit);
                assertEquals(0,e.getTruncationFileNumber());
                assertTrue(e.getEarliestTransactionId() != 0);
                assertTrue(e.getEarliestTransactionCommitTime() != null);

                /* 
                 * Very test dependent, it should be 2668 at least, but shuold
                 * be larger if some internal replicated transaction commits. 
                 * A change in log entry sizes could change this value. This 
                 * should be set to the value in of the matchpoint.
                 */
                assertTrue(e.getTruncationFileOffset() >= 2668);

                DbTruncateLog truncator = new DbTruncateLog();
                truncator.truncateLog(repEnvInfo[0].getEnvHome(),
                                      e.getTruncationFileNumber(),
                                      e.getTruncationFileOffset());
                repEnvInfo[0].openEnv
                    (new CommitPointConsistencyPolicy(commitToken, 1000,
                                                      TimeUnit.SECONDS));
                oldMaster = repEnvInfo[0].getEnv();
            }

            Database replicaDb = openDb(oldMaster);
            checkNotThere(oldMaster, replicaDb, 2, 3, 4, 5);
            replicaDb.close();

            VLSN commitVLSN = RepTestUtils.syncGroupToLastCommit(repEnvInfo,
                                                                 numNodes);
            RepTestUtils.checkNodeEquality(commitVLSN, verbose, repEnvInfo);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (db != null) {
                db.close();
                db = null;
            }
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    private Database createDb(ReplicatedEnvironment master) {
        return openDb(master, true);
    }

    private Database openDb(ReplicatedEnvironment master) {
        return openDb(master, false);
    }

    private Database openDb(ReplicatedEnvironment master, boolean allowCreate) {

        Transaction txn = master.beginTransaction(null,null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(allowCreate);
        dbConfig.setTransactional(true);
        Database db = master.openDatabase(txn, DB_NAME, dbConfig);
        txn.commit();
        return db;
    }

    /**
     * @return the commit token for the last txn used in the insert
     */
    private CommitToken doInsert(ReplicatedEnvironment master,
                                 Database db,
                                 int startVal,
                                 int endVal) {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        Transaction txn = null;

        for (int i = startVal; i <= endVal; i++) {
            txn = master.beginTransaction(null, null);
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            assertEquals(OperationStatus.SUCCESS, db.put(txn, key, data));
            if (verbose) {
                System.out.println("insert " + i);
            }
                txn.commit();
        }
        return txn.getCommitToken();
    }

    /**
     * Assert that these values are IN the database.
     */
    private void checkExists(ReplicatedEnvironment node,
                             Database db,
                             int ... values) {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        if (verbose) {
            System.err.println("Entering checkThere: node=" +
                               node.getNodeName());
        }

        for (int i : values) {
            IntegerBinding.intToEntry(i, key);
            if (verbose) {
                System.err.println("checkThere: node=" + node.getNodeName() +
                                   " " + i);
            }
            assertEquals(OperationStatus.SUCCESS,
                         db.get(null, key, data, LockMode.DEFAULT));
            assertEquals(i, IntegerBinding.entryToInt(data));
        }
    }

    /**
     * Assert that these values are NOT IN the database.
     */
    private void checkNotThere(ReplicatedEnvironment node,
                               Database db,
                               int ... values) {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        for (int i : values) {
            IntegerBinding.intToEntry(i, key);
            if (verbose) {
                System.out.println("checkNotThere: node=" + node.getNodeName()
                                   + " " + i);
            }
            assertEquals("for key " + i, OperationStatus.NOTFOUND,
                         db.get(null, key, data, LockMode.DEFAULT));
        }
    }

    /**
     * HardRecovery as invoked on a live replica. Can only occur with network
     * partitioning.
     *
     * Suppose we have nodes A,B,C. A is the master. C is partioned, and
     * therefore misses part of the replication stream. Then, through a series
     * of timing accidents, C wins mastership over A and B. A and B then have
     * to discover the problem during a syncup, and throw hard recovery
     * exceptions.
     */
    public void testHardRecoveryDeadHandle()
        throws Throwable {

        RepEnvInfo[] repEnvInfo = null;
        Database db = null;
        int numNodes = 3;
        try {
            repEnvInfo = RepTestUtils.setupEnvInfos
                (envRoot, numNodes, RepTestUtils.SYNC_SYNC_NONE_DURABILITY);

            /* 
             * Start the master first, to ensure that it is the master, and then
             * start the rest of the group.
             */
            ReplicatedEnvironment master = repEnvInfo[0].openEnv();
            assert master != null;

            db = createDb(master);
            CommitToken commitToken = doInsert(master, db, 1, 1);
            CommitPointConsistencyPolicy cp =
                new CommitPointConsistencyPolicy(commitToken, 1000,
                                                 TimeUnit.SECONDS);

            for (int i = 1; i < numNodes; i++) {
                repEnvInfo[i].openEnv(cp);
            }

            /*
             * After node1 and node2 join, make sure that their presence in the
             * rep group db is propagated before we do a forceMaster. When a
             * node calls for an election, it must have its own id available to
             * itself from the rep group db on disk. If it doesn't, it will
             * send an election request with an illegal node id. In real life,
             * this can never happen, because a node that does not have its own
             * id won't win mastership, since others will be ahead of it.
             */
            commitToken = doInsert(master, db, 2, 2);
            TransactionConfig txnConfig = new TransactionConfig();
            txnConfig.setConsistencyPolicy
                (new CommitPointConsistencyPolicy(commitToken, 1000,
                                                  TimeUnit.SECONDS));

            for (int i = 1; i < numNodes; i++) {
                Transaction txn = 
                    repEnvInfo[i].getEnv().beginTransaction(null, txnConfig);
                txn.commit();
            }

            /*
             * Mimic a network partition by forcing one replica to not see the
             * incoming LNs. Do some work, which that last replica doesn't see
             * and then force the laggard to become the master. This will
             * create a case where the current master has to do a hard
             * recovery.
             */
            int lastIndex = numNodes - 1;
            WaitForMasterListener masterWaiter = new WaitForMasterListener();
            ReplicatedEnvironment forcedMaster = repEnvInfo[lastIndex].getEnv();
            forcedMaster.setStateChangeListener(masterWaiter);
            RepNode lastRepNode =  repEnvInfo[lastIndex].getRepNode();
            lastRepNode.replica().setDontProcessStream();

            commitToken = doInsert(master, db, 3, 4);
            db.close();
            db = null;
            logger.info("Before force");
            
            /*
             * waitForOldMaster and waitForNode2 are latches that will help
             * us when we force node 3 to become a master. We'll use these
             * latches to know that nodes 1 and nodes 2 have finished a syncup.
             */
            CountDownLatch waitForOldMaster = setupWaitForSyncup(master);
            CountDownLatch waitForNode2 = 
                setupWaitForSyncup(repEnvInfo[1].getEnv());

            /* 
             * Make node3 the master. Make sure that it did not see the
             * work done while it was in its fake network partitioned state.
             */
            lastRepNode.forceMaster(true);
            logger.info("After force");
            masterWaiter.awaitMastership();

            db = openDb(forcedMaster);
            checkNotThere(forcedMaster, db, 3, 4);
            checkExists(forcedMaster, db, 1, 2);
            db.close();

            /*
             * At this point, the other nodes should have thrown a
             * RollbackException, and become invalid.
             */
            checkForHardRecovery(waitForOldMaster, repEnvInfo[0]);
            checkForHardRecovery(waitForNode2, repEnvInfo[1]);

            /* 
             * Restart the group, make it do some other work and check
             * that the group has identical contents.
             */
            logger.info("restarting nodes which did hard recovery");
            RepTestUtils.restartReplicas(repEnvInfo[0], repEnvInfo[1]);
            logger.info("sync group");
            VLSN commitVLSN = RepTestUtils.syncGroupToLastCommit(repEnvInfo,
                                                                 numNodes);

            logger.info("run check");
            RepTestUtils.checkNodeEquality(commitVLSN, verbose, repEnvInfo);
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (db != null) {
                db.close();
                db = null;
            }
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    private CountDownLatch setupWaitForSyncup(ReplicatedEnvironment master) {
        final CountDownLatch  waiter = new CountDownLatch(1);
        
        TestHook<Object> syncupFinished = new TestHook<Object>() {
            public void doHook() throws InterruptedException {
                waiter.countDown();
            }
        };

        RepInternal.getRepImpl(master).getRepNode().
            replica().setReplicaFeederSyncupHook(syncupFinished);
        return waiter;
    }

    /**
     * Make sure that this node has thrown a RollbackException.
     */
    private void checkForHardRecovery(CountDownLatch syncupFinished,
                                      RepEnvInfo envInfo)
        throws Throwable {

        syncupFinished.await();
        logger.info(envInfo.getEnv().getNodeName() + " becomes replica");

        try {
            ReplicatedEnvironment.State state = envInfo.getEnv().getState();
            fail("Should have seen rollback exception, got state of " +
                 state);
        } catch (RollbackException expected) {
            assertTrue(expected.getEarliestTransactionId() != 0);
            assertTrue(expected.getEarliestTransactionCommitTime() != null);
            logger.info("expected = " + expected.toString());
            envInfo.closeEnv();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
