/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: JoinGroupTest.java,v 1.29 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.utilint.VLSN;

public class JoinGroupTest extends RepTestBase {

    /**
     * Simulates the scenario where an entire group goes down and is restarted.
     */
    public void testAllJoinLeaveJoinGroup()
        throws DatabaseException,
               InterruptedException {

        createGroup();
        ReplicatedEnvironment masterRep = repEnvInfo[0].getEnv();
        populateDB(masterRep, TEST_DB_NAME, 100);
        RepTestUtils.syncGroupToLastCommit(repEnvInfo, repEnvInfo.length);

        /* Shutdown the entire group. */
        closeNodes(repEnvInfo);

        /* Restart the group. */
        restartNodes(repEnvInfo);
    }

    // Tests repeated opens of the same environment
    public void testRepeatedOpen()
        throws UnknownMasterException, DatabaseException {

        /* All nodes have joined. */
        createGroup();

        /* Already joined, rejoin master. */
        State state = repEnvInfo[0].getEnv().getState();
        assertEquals(State.MASTER, state);

        /* Already joined, rejoin replica, by creating another handle. */
        ReplicatedEnvironment r1Handle = new ReplicatedEnvironment
            (repEnvInfo[1].getEnvHome(),
             repEnvInfo[1].getRepConfig(),
             repEnvInfo[1].getEnvConfig());
        state = r1Handle.getState();
        assertEquals(State.REPLICA, state);
        r1Handle.close();
    }

    public void testDefaultJoinGroup()
        throws UnknownMasterException,
               DatabaseException {

        createGroup();
        ReplicatedEnvironment masterRep = repEnvInfo[0].getEnv();
        assertEquals(State.MASTER, masterRep.getState());
        leaveGroupAllButMaster();
        /* Populates just the master. */
        CommitToken ct = populateDB(masterRep, TEST_DB_NAME, 100);

        /* Replicas should have caught up when they re-open their handles. */
        for (RepEnvInfo ri : repEnvInfo) {
            ReplicatedEnvironment rep =
                (ri.getEnv() == null) ? ri.openEnv() : ri.getEnv();
            VLSN repVLSN = RepInternal.getRepImpl(rep).
                getVLSNIndex().getRange().getLast();
            assertTrue(new VLSN(ct.getVLSN()).compareTo(repVLSN) <= 0);
        }
    }

    public void testDefaultJoinGroupHelper()
        throws UnknownMasterException,
               DatabaseException {

        for (int i = 0; i < repEnvInfo.length; i++) {
            RepEnvInfo ri = repEnvInfo[i];
            if ((i + 1) == repEnvInfo.length) {
                /* Use a non-master helper for the last replicator. */
                ReplicationConfig config =
                    RepTestUtils.createRepConfig((short) (i + 1));
                String hpPairs = "";
                // Skip the master, use all the other nodes
                for (int j = 1; j < i; j++) {
                    hpPairs +=
                        "," + repEnvInfo[j].getRepConfig().getNodeHostPort();
                }
                hpPairs = hpPairs.substring(1);
                config.setHelperHosts(hpPairs);
                File envHome = ri.getEnvHome();
                ri = repEnvInfo[i] =
                        new RepEnvInfo(envHome,
                                       config,
                                       RepTestUtils.createEnvConfig
                                       (Durability.COMMIT_SYNC));
            }
            ri.openEnv();
            State state = ri.getEnv().getState();
            assertEquals((i == 0) ? State.MASTER : State.REPLICA, state);
        }
    }

    public void testTimeConsistencyJoinGroup()
        throws UnknownMasterException,
               DatabaseException {

        createGroup();
        ReplicatedEnvironment masterRep = repEnvInfo[0].getEnv();
        assertEquals(State.MASTER, masterRep.getState());

        leaveGroupAllButMaster();
        /* Populates just the master. */
        populateDB(masterRep, TEST_DB_NAME, 100);

        repEnvInfo[1].openEnv
            (new TimeConsistencyPolicy(1, TimeUnit.MILLISECONDS,
                                       RepTestUtils.MINUTE_MS,
                                       TimeUnit.MILLISECONDS));
        ReplicatedEnvironmentStats stats =
            repEnvInfo[1].getEnv().getRepStats(StatsConfig.DEFAULT);

        assertEquals(1, stats.getTrackerLagConsistencyWaits());
        assertTrue(stats.getTrackerLagConsistencyWaitMs() > 0);
    }

    public void testVLSNConsistencyJoinGroup()
        throws UnknownMasterException,
               DatabaseException,
               InterruptedException {

        createGroup();
        ReplicatedEnvironment masterRep = repEnvInfo[0].getEnv();
        assertEquals(State.MASTER, masterRep.getState());
        leaveGroupAllButMaster();
        /* Populates just the master. */
        populateDB(masterRep, TEST_DB_NAME, 100);
        UUID uuid =
            RepInternal.getRepImpl(masterRep).getRepNode().getUUID();
        long masterVLSN = RepInternal.getRepImpl(masterRep).
            getVLSNIndex().getRange().getLast().
            getSequence()+2 /* 1 new entry + txn commit record */;

        JoinCommitThread jt =
            new JoinCommitThread(new CommitToken(uuid,masterVLSN),
                                 repEnvInfo[1]);
        jt.start();
        Thread.sleep(5000);
        // supply the vlsn it's waiting for. Record count MUST sync up with
        // the expected masterVLSN
        populateDB(masterRep, TEST_DB_NAME, 1);
        jt.join(JOIN_WAIT_TIME);

        assertTrue(!jt.isAlive());
        assertNull("Join thread exception", jt.testException);
    }

    public void testJoinGroupTimeout()
        throws UnknownMasterException,
               DatabaseException {

        assertTrue(repEnvInfo.length > 2);
        createGroup();
        final RepEnvInfo riMaster = repEnvInfo[0];
        assertEquals(State.MASTER, riMaster.getEnv().getState());
        leaveGroupAllButMaster();
        riMaster.closeEnv();

        // Can't hold elections need at least two nodes, so timeout
        try {
            ReplicationConfig config = riMaster.getRepConfig();
            config.setConfigParam
                (RepParams.ENV_SETUP_TIMEOUT.getName(), "5 s");
            State status = riMaster.openEnv().getState();
            fail("Joined group in state: " + status);
        } catch (UnknownMasterException e) {
            // Expected exception
        }
    }

    /* Utility thread for joining group. */
    class JoinCommitThread extends Thread {
        final RepEnvInfo replicator;
        final CommitToken commitToken;
        Exception testException = null;

        JoinCommitThread(CommitToken commitToken, RepEnvInfo replicator) {
            this.commitToken = commitToken;
            this.replicator = replicator;
        }

        @Override
        public void run() {
            try {
                ReplicatedEnvironment repenv= replicator.openEnv
                    (new CommitPointConsistencyPolicy(commitToken,
                                                      RepTestUtils.MINUTE_MS,
                                                      TimeUnit.MILLISECONDS));
                assertEquals(ReplicatedEnvironment.State.REPLICA,
                             repenv.getState());
                ReplicatedEnvironmentStats stats =
                    replicator.getEnv().getRepStats(StatsConfig.DEFAULT);

                assertEquals(1, stats.getTrackerVLSNConsistencyWaits());
                assertTrue(stats.getTrackerVLSNConsistencyWaitMs() > 0);
            } catch (UnknownMasterException e) {
                testException = e;
                throw new RuntimeException(e);
            } catch (DatabaseException e) {
                testException = e;
                throw new RuntimeException(e);
            }
        }
    }
}
