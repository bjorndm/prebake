/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RollbackTest.java,v 1.36 2010/01/04 15:51:06 cwl Exp $
 */
package com.sleepycat.je.rep.txn;

import java.io.File;
import java.util.logging.Logger;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.rep.LogOverwriteException;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.txn.RollbackWorkload.DatabaseOpsStraddlesMatchpoint;
import com.sleepycat.je.rep.txn.RollbackWorkload.IncompleteTxnAfterMatchpoint;
import com.sleepycat.je.rep.txn.RollbackWorkload.IncompleteTxnBeforeMatchpoint;
import com.sleepycat.je.rep.txn.RollbackWorkload.IncompleteTxnStraddlesMatchpoint;
import com.sleepycat.je.rep.txn.RollbackWorkload.SteadyWork;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.util.DbBackup;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.VLSN;

/**
 * Test that a replica can rollback an replay active txn for syncup.
 * Test cases:
 * - Replay txn has only logged log entries that follow the syncup matchpoint
 * and must be entirely rolled back.
 *
 * - Replay txn has only logged log entries that precede the syncup matchpoint
 * and doesn not have to be rolled back at all.
 *
 * - Replay txn has logged log entries that both precede and follow the
 *   syncup matchpoint, and the txn must be partially rolled back.
 *
 * TRY: master fails
 *      replica fails
 *
 * The txn should have
 * - inserts
 * - delete
 * - reuse of a BIN slot
 * - intermediate versions within the same txn (the same record is modified
 * multiple times within the txn.
 */
public class RollbackTest extends TestCase {

    private final Logger logger;
    private final boolean verbose = Boolean.getBoolean("verbose");

    /* Replication tests use multiple environments. */
    private final File envRoot;

    public RollbackTest() {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testDbOpsRollback() 
        throws Throwable {

        try {
            masterDiesAndRejoins(new DatabaseOpsStraddlesMatchpoint());
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;        
        }
    }

    public void testTxnEndBeforeMatchpoint()
        throws Throwable {

        masterDiesAndRejoins(new IncompleteTxnBeforeMatchpoint());
    }

    public void testTxnEndAfterMatchpoint()
        throws Throwable {

        masterDiesAndRejoins(new IncompleteTxnAfterMatchpoint());
    }

    public void testTxnStraddleMatchpoint()
        throws Throwable {

        masterDiesAndRejoins(new IncompleteTxnStraddlesMatchpoint());
    }

    public void testReplicasFlip()
        throws Throwable {

        replicasDieAndRejoin(new SteadyWork(), 10);
    }

    /*
     * Test the API: RepImpl.setBackupProhibited would disable the DbBackup in
     * DbBackup.startBackup, may be caused by Replay.rollback().
     */
    public void testRollingBackDbBackupAPI()
        throws Throwable {

        RepEnvInfo[] repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 1);
        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
        RepImpl repImpl = RepInternal.getRepImpl(master);

        DbBackup backupHelper = new DbBackup(master);
        repImpl.setBackupProhibited(true);

        try {
            backupHelper.startBackup();
            fail("Should throw out a LogOverwriteException here.");
        } catch (LogOverwriteException e) {
            /* Expect a LogOverwriteException here. */
        }

        repImpl.setBackupProhibited(false);
        try {
            backupHelper.startBackup();
            backupHelper.endBackup();
        } catch (Exception e) {
            fail("Shouldn't get an exception here.");
        } finally {
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    /*
     * Test the API: RepImpl.invalidateDbBackups would disable the DbBackup
     * at endBackup, may be caused by Replay.rollback().
     */
    public void testRollBackInvalidateDbBackup()
        throws Exception {

        RepEnvInfo[] repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 1);
        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
        final RepImpl repImpl = RepInternal.getRepImpl(master);

        DbBackup backupHelper = new DbBackup(master);
        backupHelper.startBackup();

        backupHelper.setTestHook(new TestHook<Object>() {
            public void doHook() {
                repImpl.invalidateBackups(8L);
            }

            public Object getHookValue() {
                throw new UnsupportedOperationException();
            }

            public void doIOHook() {
                throw new UnsupportedOperationException();
            }

            public void hookSetup() {
                throw new UnsupportedOperationException();
            }
        });

        try {
            backupHelper.endBackup();
            fail("Should throw out a LogOverwriteException here.");
        } catch (LogOverwriteException e) {
            /* Expect to get a LogOverwriteException here. */
        } finally {
            RepTestUtils.shutdownRepEnvs(repEnvInfo);
        }
    }

    /**
     * Create 3 nodes and replicate operations.
     * Kill off the master, and make the other two resume. This will require
     * a syncup and a rollback of any operations after the matchpoint.
     */
    private void masterDiesAndRejoins(RollbackWorkload workload)
        throws Throwable {


        RepEnvInfo[] repEnvInfo = null;

        try {
            /* Create a  3 node group */
            repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 3);
            ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
            logger.severe("master=" + master);

            /*
             * Run a workload against the master. Sync up the group and check
             * that all nodes have the same contents. This first workload must
             * end with in-progress, uncommitted transactions.
             */
            workload.beforeMasterCrash(master);
            VLSN lastVLSN = VLSN.NULL_VLSN;
            if (workload.noLockConflict()) {
                lastVLSN = checkIfWholeGroupInSync(master, repEnvInfo,
                                                    workload);
            }

            /*
             * Crash the master, find a new master.
             */
            RepEnvInfo oldMaster =
                repEnvInfo[RepInternal.getNodeId(master) - 1];
            master = crashMasterAndElectNewMaster(master, repEnvInfo);
            RepEnvInfo newMaster =
                repEnvInfo[RepInternal.getNodeId(master) - 1];
            logger.severe("newmaster=" + master);
            RepEnvInfo alwaysReplica = null;
            for (RepEnvInfo info : repEnvInfo) {
                if ((info != oldMaster) && (info != newMaster)) {
                    alwaysReplica = info;
                    break;
                }
            }

            /*
             * Check that the remaining two nodes only contain committed
             * updates.
             * TODO: check that the number of group members is 2.
             */
            assertTrue(workload.containsSavedData(master));
            RepTestUtils.checkNodeEquality(lastVLSN, verbose, repEnvInfo);

            /*
             * Do some work against the new master, while the old master is
             * asleep. Note that the first workload may have contained
             * in-flight transactions, so this may result in the rollback of
             * some transactions in the first workload.
             */
            workload.afterMasterCrashBeforeResumption(master);

            /*
             * The intent of this test is that the work after crash will end on
             * an incomplete txn. Check for that.
             */
            lastVLSN = ensureDistinctLastAndSyncVLSN(master, repEnvInfo);

            /* Now bring up the old master. */
            logger.info("Bring up old master");
            oldMaster.openEnv();

            logger.info("Old master joined");
            RepTestUtils.syncGroupToVLSN(repEnvInfo, repEnvInfo.length,
                                         lastVLSN);
            logger.info("Old master synced");

            /*
             * Check that all nodes only contain committed updates.
             */
            workload.releaseDbLocks();
            assertTrue(workload.containsSavedData(master));
            RepTestUtils.checkNodeEquality(lastVLSN, verbose, repEnvInfo);

            /*
             * Now crash the node that has never been a master. Do some work
             * without it, then recover that node, then do a verification
             * check.  This exercises the recovery of a log that has syncups in
             * it.
             */
            alwaysReplica.abnormalCloseEnv();
            workload.afterReplicaCrash(master);

            lastVLSN = RepInternal.getRepImpl(master).getVLSNIndex().
                getRange().getLast();
            RepTestUtils.syncGroupToVLSN(repEnvInfo, 2, lastVLSN);
            alwaysReplica.openEnv();
            RepTestUtils.syncGroupToVLSN(repEnvInfo, 3, lastVLSN);
            assertTrue(workload.containsSavedData(master));
            RepTestUtils.checkNodeEquality(lastVLSN, verbose, repEnvInfo);
            RepTestUtils.checkUtilizationProfile(repEnvInfo);

            workload.close();
            for (RepEnvInfo repi : repEnvInfo) {
                /*
                 * We're done with the test. Bringing down these replicators
                 * forcibly, without closing transactions and whatnot.
                 */
                repi.abnormalCloseEnv();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void replicasDieAndRejoin(RollbackWorkload workload,
                                      int numIterations)
        throws Throwable {


        RepEnvInfo[] repEnvInfo = null;

        try {
            /* Create a  3 node group. Assign identities. */
            repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 3);
            ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
            logger.severe("master=" + master);

            RepEnvInfo replicaA = null;
            RepEnvInfo replicaB = null;

            for (RepEnvInfo info : repEnvInfo) {
                if (info.getEnv().getState().isMaster()) {
                    continue;
                }

                if (replicaA == null) {
                    replicaA = info;
                } else {
                    replicaB = info;
                }
            }

            /*
             * For the sake of easy test writing, make sure numIterations is an
             * even number.
             */
            assertTrue((numIterations % 2) == 0);
            replicaA.abnormalCloseEnv();
            for (int i = 0; i < numIterations; i++) {
                workload.masterSteadyWork(master);
                waitForReplicaToSync(master, repEnvInfo);
                if ((i % 2) == 0) {
                    flushLogAndCrash(replicaB);
                    replicaA.openEnv();
                } else {
                    flushLogAndCrash(replicaA);
                    replicaB.openEnv();
                }
                waitForReplicaToSync(master, repEnvInfo);
            }
            replicaA.openEnv();

            VLSN lastVLSN = RepInternal.getRepImpl(master).getVLSNIndex().
                getRange().getLast();
            RepTestUtils.syncGroupToVLSN(repEnvInfo,
                                         repEnvInfo.length,
                                         lastVLSN);

            assertTrue(workload.containsAllData(master));
            RepTestUtils.checkNodeEquality(lastVLSN, verbose, repEnvInfo);

            workload.close();
            for (RepEnvInfo repi : repEnvInfo) {
                /*
                 * We're done with the test. Bringing down these replicators
                 * forcibly, without closing transactions and whatnot.
                 */
                repi.abnormalCloseEnv();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void flushLogAndCrash(RepEnvInfo replica) {
        DbInternal.getEnvironmentImpl(replica.getEnv()).getLogManager().flush();
        replica.abnormalCloseEnv();
    }

    /**
     * Syncup the group and check for these requirements:
     *  - the master has all the data we expect
     *  - the replicas have all the data that is on the master.

     *  - the last VLSN is not a sync VLSN. We want to ensure that the
     * matchpoint is not the last VLSN, so the test will need to do rollback
     * @throws InterruptedException
     * @return lastVLSN on the master
     */
    private VLSN checkIfWholeGroupInSync(ReplicatedEnvironment master,
                                         RepEnvInfo[] repEnvInfo,
                                         RollbackWorkload workload)
        throws InterruptedException {

        /*
         * Make sure we're testing partial rollbacks, and that the replication
         * stream is poised at a place where the last sync VLSN != lastVLSN.
         */
        VLSN lastVLSN = ensureDistinctLastAndSyncVLSN(master, repEnvInfo);

        RepTestUtils.syncGroupToVLSN(repEnvInfo, repEnvInfo.length, lastVLSN);

        /*
         * All nodes in the group should have the same data, and it should
         * consist of committed and uncommitted updates.
         */
        assertTrue(workload.containsAllData(master));
        RepTestUtils.checkNodeEquality(lastVLSN, verbose, repEnvInfo);

        return lastVLSN;
    }

    /* Just check if the replica is in sync. */
    private void waitForReplicaToSync(ReplicatedEnvironment master,
                                      RepEnvInfo[] repEnvInfo)
        throws InterruptedException {

        VLSN lastVLSN = RepInternal.getRepImpl(master).getVLSNIndex().
            getRange().getLast();
        RepTestUtils.syncGroupToVLSN(repEnvInfo, 2, lastVLSN);
    }

    /**
     * Crash the current master, and wait until the group elects a new one.
     */
    private ReplicatedEnvironment
        crashMasterAndElectNewMaster(ReplicatedEnvironment master,
                                     RepEnvInfo[] repEnvInfo) {

        int masterIndex = RepInternal.getNodeId(master) - 1;

        logger.info("Crashing " + master.getNodeName());
        repEnvInfo[masterIndex].abnormalCloseEnv();

        logger.info("Rejoining");
        ReplicatedEnvironment newMaster =
            RepTestUtils.openRepEnvsJoin(repEnvInfo);

        logger.info("New master = " + newMaster.getNodeName());
        return newMaster;
    }

    /**
     * In this test, we often want to check that the last item in the
     * replicated stream is not a matchpoint candidate (that VLSNRange.lastVLSN
     * != VLSNRange.lastSync) There's nothing wrong intrinsically with that
     * being so, it's just that this test is trying to ensure that we test
     * partial rollbacks.
     * @return lastVLSN
     * @throws InterruptedException
     */
    private VLSN ensureDistinctLastAndSyncVLSN(ReplicatedEnvironment master,
                                               RepEnvInfo[] repEnvInfo)
        throws InterruptedException {

        VLSNIndex vlsnIndex = RepInternal.getRepImpl(master).getVLSNIndex();
        VLSNRange range = vlsnIndex.getRange();
        VLSN lastVLSN = range.getLast();
        VLSN syncVLSN = range.getLastSync();
        assertFalse("lastVLSN = " + lastVLSN + " syncVLSN = " +
                    syncVLSN,
                    lastVLSN.equals(syncVLSN));

        return lastVLSN;
    }
}
