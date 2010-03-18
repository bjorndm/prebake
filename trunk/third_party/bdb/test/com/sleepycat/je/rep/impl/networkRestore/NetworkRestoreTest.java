/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NetworkRestoreTest.java,v 1.31 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.impl.networkRestore;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.impl.networkRestore.NetworkBackup.InsufficientVLSNRangeException;
import com.sleepycat.je.rep.impl.networkRestore.NetworkBackup.LoadThresholdExceededException;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.rep.utilint.ServiceDispatcher.ServiceConnectFailedException;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.utilint.VLSN;

public class NetworkRestoreTest extends RepTestBase {

    /*
     * Repeats of network restore operations, as the master advances the log
     * with modifications during each iteration.
     */
    private static int RESTORE_CYCLES = 5;

    @Override
    protected void setUp()
        throws Exception {

        super.setUp();
    }

    @Override
    protected void tearDown()
        throws Exception {

        super.tearDown();
    }

    public void testLogProviders() {
        configureForMaxCleaning(5);
        final RepEnvInfo minfo = repEnvInfo[0];

        createGroup();
        populateDB(minfo.getEnv(), TEST_DB_NAME, 100);

        /* The node that will be use for network restores. */
        RepEnvInfo nrInfo = repEnvInfo[1];

        /* restore from master. */
        doAndCheckRestore(nrInfo, minfo);
        /* Check restore from specific Replica. */
        doAndCheckRestore(nrInfo, repEnvInfo[2]);
        /* restore from self should fail. */
        try {
            doAndCheckRestore(nrInfo, repEnvInfo[1]);
            fail("exception expected");
        } catch (EnvironmentFailureException e) {
            // Expected. Cannot restore from just yourself.
        }
    }

    public void testConfigError() {
        configureForMaxCleaning(5);
        final RepEnvInfo minfo = repEnvInfo[0];

        createGroup();
        populateDB(minfo.getEnv(), TEST_DB_NAME, 100);
        RepEnvInfo nrInfo = repEnvInfo[1];
        nrInfo.closeEnv();
        shiftVLSNRight(repEnvInfo[0].getEnv());
        try {
            nrInfo.openEnv();
            fail("exception expected");
        } catch (InsufficientLogException e) {
            NetworkRestore networkRestore = new NetworkRestore();

            final ReplicationConfig repConfig = repEnvInfo[0].getRepConfig();
            // bad node name
            repConfig.setNodeName("badname");
            ReplicationNode restoreNode = new RepNodeImpl(repConfig);
            final NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setLogProviders(Arrays.asList(restoreNode));
            try {
                networkRestore.execute(e, config);
                fail("exception expected");
            } catch (IllegalArgumentException iae) {
                // Expected
            }
        }
    }

    /*
     * Creates conditions for a network restore at nrInfo and then restores the
     * node form a specific member.
     */
    private void doAndCheckRestore(RepEnvInfo nrInfo,
                                   RepEnvInfo restoreFromInfo) {
        nrInfo.closeEnv();
        shiftVLSNRight(repEnvInfo[0].getEnv());
        try {
            nrInfo.openEnv();
            fail("exception expected");
        } catch (InsufficientLogException e) {
            NetworkRestore networkRestore = new NetworkRestore();
            ReplicationNode restoreNode =
                new RepNodeImpl(restoreFromInfo.getRepConfig());
            final NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setLogProviders(Arrays.asList(restoreNode));
            networkRestore.execute(e, config);
            assertEquals(restoreNode, networkRestore.getLogProvider());
            nrInfo.openEnv();
        }
    }

    /**
     * This is really multiple tests in one. It tests network restore with a
     * replica in each of the following three states:
     *
     * 1) A brand new node joining the group and needing a network restore.
     *
     * 2) An existing node with its own unique log needing a network restore.
     *
     * 3) Repeated network restores, reflecting a mature node.
     */
    public void testBasic()
        throws DatabaseException, Exception {

        configureForMaxCleaning(2);

        final RepEnvInfo info1 = repEnvInfo[0];
        RepEnvInfo info2 = repEnvInfo[1];

        ReplicatedEnvironment masterRep = info1.openEnv();
        Environment menv = masterRep;
        EnvironmentMutableConfig mconfig = menv.getMutableConfig();
        mconfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                               "false");
        menv.setMutableConfig(mconfig);

        /*
         * Have just the master join first. We do this to test the special case
         * of a brand new node joining a group and needing VLSN 1. The same
         * node then rejoins with its VLSN > 1 to test subsequent rejoins
         * where the node has already participated in the replication.
         */
        populateDB(masterRep, TEST_DB_NAME, 100);

        mconfig = menv.getMutableConfig();
        mconfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                               "true");
        menv.setMutableConfig(mconfig);

        File cenvDir = info2.getEnvHome();
        final int cid = 2;

        for (int i=0; i < RESTORE_CYCLES; i++) {

            leaveGroupAllButMaster();
            shiftVLSNRight(masterRep);
            RepNodeImpl memberPrev =
                info1.getRepNode().getGroup().getMember
                 (info2.getRepConfig().getNodeName());
            /* Node1 is not known on the first iteration. */
            final VLSN prevSync = (i == 0) ? null :
                memberPrev.getBarrierState().getLastCBVLSN();
            try {
                // Should force a network restore
                info2.openEnv();
                fail("exception expected");
            } catch (InsufficientLogException e) {
                RepNodeImpl member = info1.getRepNode().getGroup().getMember
                    (info2.getRepConfig().getNodeName());
                /*
                 * The sync state should have been advanced to help contribute
                 * to the global CBVLSN and prevent it from advancing.
                 */
                final VLSN currSync = member.getBarrierState().getLastCBVLSN();
                assertTrue((i == 0) || currSync.compareTo(prevSync) >= 0);

                NetworkRestore networkRestore = new NetworkRestore();
                networkRestore.execute(e, new NetworkRestoreConfig());
                /* Create a replacement replicator. */
                info2 = RepTestUtils.setupEnvInfo
                    (cenvDir,
                     RepTestUtils.DEFAULT_DURABILITY,
                     cid,
                     info1);
                info2.openEnv();
            }
            /* Verify that we can continue with the "restored" log files. */
            populateDB(masterRep, TEST_DB_NAME, 100, 100);
            VLSN commitVLSN =
                RepTestUtils.syncGroupToLastCommit(repEnvInfo, 2);
            RepTestUtils.checkNodeEquality(commitVLSN, false, repEnvInfo);
            info2.closeEnv();
        }
    }

    private void configureForMaxCleaning(int size) {
        for (int i=0; i < size; i++) {
            /*
             * Obsolete right away for test purposes, so that the max CBVLSN
             * is effectively the global CBVLSN
             */
            repEnvInfo[i].getRepConfig().setConfigParam
                (RepParams.REP_STREAM_TIMEOUT.getName(), "0");
            repEnvInfo[i].getRepConfig().setConfigParam
                (RepParams.CBVLSN_PAD.getName(), "500");
            /* Make it easy to create cleaner fodder. */
            repEnvInfo[i].getEnvConfig().setConfigParam
                (EnvironmentParams.LOG_FILE_MAX.getName(), "10000");
            repEnvInfo[i].getEnvConfig().setConfigParam
                (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "90");
            repEnvInfo[i].getEnvConfig().setConfigParam
            (EnvironmentParams.CLEANER_MIN_FILE_UTILIZATION.getName(), "90");
        }
    }

    /*
     * Provoke sufficient log cleaning to move the entire VLSN right
     * sufficiently that the new VLSN range no longer overlaps the VLSN
     * range upon entry thus guaranteeing a LogFileRefreshException.
     */
    private void shiftVLSNRight(ReplicatedEnvironment menv) {
        /* Shift the vlsn range window. */

        RepImpl menvImpl = repEnvInfo[0].getRepImpl();
        final VLSNIndex vlsnIndex = menvImpl.getRepNode().getVLSNIndex();
        VLSN masterHigh = vlsnIndex.getRange().getLast();

        CheckpointConfig checkpointConfig = new CheckpointConfig();
        checkpointConfig.setForce(true);

        do {
            /* Populate just the master, leaving the replica behind */
            /* Re-populate with the same keys to create Cleaner fodder. */
            populateDB(menv, TEST_DB_NAME, 100);
            /*
             * Sleep to permit the cbvlsn on the master to be updated. It's
             * done with the period: FeederManager.MASTER_CHANGE_CHECK_TIMEOUT
             */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                fail("unexpected interrupt");
            }
            menv.cleanLog();
            menv.checkpoint(checkpointConfig);
        } while (masterHigh.compareTo(vlsnIndex.getRange().getFirst()) > 0);

        masterHigh = vlsnIndex.getRange().getLast();
    }

    /*
     * Test the API: RepNode.shutdownNetworkBackup/restartNetworkBackup service
     * used to disable the service around a replica syncup operation.
     */
    public void testLockout()
        throws IOException {

        repEnvInfo[0].openEnv();
        RepNode repNode = repEnvInfo[0].getRepNode();
        leaveGroupAllButMaster();

        repNode.shutdownNetworkBackup();
        File backupDir =
            new File(repEnvInfo[1].getEnvHome().getCanonicalPath() +
                     ".backup");
        backupDir.mkdir();
        assertTrue(backupDir.exists());

        try {
            NetworkBackup backup =
                new NetworkBackup(repNode.getSocket(),
                                  backupDir,
                                  new NameIdPair("n1", (short)1),
                                  true);
            backup.execute();
            fail("expected exception service should not have been available");
        } catch (ServiceConnectFailedException e) {
            /* Expected. */
        } catch (Exception e) {
            fail("unexpected exception");
        }

        repNode.restartNetworkBackup();
        try {
            NetworkBackup backup =
                new NetworkBackup(repNode.getSocket(),
                                  backupDir,
                                  new NameIdPair("n1", (short)1),
                                  true);
            backup.execute();
        }  catch (Exception e) {
            fail("unexpected exception:" + e);
        }
    }


    /**
     * Verify that a NetworkBackup that's in progress is aborted by
     * repNode.shutdownNetworkRestore() and therefore during a rollback
     * operation.
     */
    public void testNBAbortOnSyncup()
        throws IOException,
               DatabaseException,
               ServiceConnectFailedException,
               LoadThresholdExceededException, InsufficientVLSNRangeException {

        repEnvInfo[0].openEnv();
        final RepNode repNode = repEnvInfo[0].getRepNode();
        leaveGroupAllButMaster();
        File backupDir =
            new File(repEnvInfo[1].getEnvHome().getCanonicalPath() +
                     ".backup");

        NetworkBackup backup =
            new NetworkBackup(repNode.getSocket(),
                              backupDir,
                              new NameIdPair("n1", (short)1),
                              true);
        CyclicBarrier testBarrier =
            new CyclicBarrier(1, new Runnable() {
                public void run() {
                    /* The syncup should kill the NB */
                    repNode.shutdownNetworkBackup();
                }
            }
        );
        backup.setTestBarrier(testBarrier);
        try {
            backup.execute();
            fail("Expected exception");
        } catch(IOException e) {
            /* Expected exception as in progress service was terminated. */
        }
    }
}
