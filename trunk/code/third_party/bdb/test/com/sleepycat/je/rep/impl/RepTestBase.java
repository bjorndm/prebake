/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepTestBase.java,v 1.23 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.impl;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.CommitToken;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.UnknownMasterException;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;

public abstract class RepTestBase extends TestCase {

    /**
         * Used to start up an existing group. Each environment must be opened in
         * its own thread, since the open of the environment does not return until
         * an election has been concluded and a Master is in place.
         */
        protected static class EnvOpenThread extends Thread {
            final RepEnvInfo threadRepInfo;
            Throwable testException = null;

            EnvOpenThread(RepEnvInfo info) {
                this.threadRepInfo = info;
            }

            @Override
            public void run() {
                try {
                    threadRepInfo.openEnv();
                } catch (Throwable e) {
                    testException = e;
                }
            }
        }

    /**
         * Listener used to determine when a Master becomes available, by tripping
         * the count down latch.
         */
        protected static class MasterListener implements StateChangeListener{
            final CountDownLatch masterLatch;

            public MasterListener(CountDownLatch masterLatch) {
                super();
                this.masterLatch = masterLatch;
            }

            public void stateChange(StateChangeEvent stateChangeEvent)
                throws RuntimeException {

                if (stateChangeEvent.getState().isMaster()) {
                    masterLatch.countDown();
                }
            }
        }

    protected final File envRoot =
        new File(System.getProperty(TestUtils.DEST_DIR));
    protected int groupSize = 5;
    protected RepEnvInfo[] repEnvInfo = null;
    protected DatabaseConfig dbconfig;
    protected final DatabaseEntry key = new DatabaseEntry(new byte[]{1});
    protected final DatabaseEntry data = new DatabaseEntry(new byte[]{100});
    protected static final String TEST_DB_NAME = "TestDB";

    /* Max time to wait for consistency to be established. */
    protected static final int CONSISTENCY_TIMEOUT_MS= 10000;
    public static final int JOIN_WAIT_TIME = 5000;

    public RepTestBase() {
        super();
    }

    public RepTestBase(String name) {
        super(name);
    }

    @Override
    protected void setUp()
        throws Exception {

        RepTestUtils.removeRepEnvironments(envRoot);
        dbconfig = new DatabaseConfig();
        dbconfig.setAllowCreate(true);
        dbconfig.setTransactional(true);
        dbconfig.setSortedDuplicates(false);

        repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, groupSize);
    }

    /**
     * @throws Exception in subclasses.
     */
    @Override
    protected void tearDown()
        throws Exception {

        RepTestUtils.shutdownRepEnvs(repEnvInfo);
        RepTestUtils.removeRepEnvironments(envRoot);
        /* Verify that all environments were indeed closed. */
        Collection<EnvironmentImpl> residualImpls =
            DbEnvPool.getInstance().getEnvImpls();
        if (residualImpls.size() != 0) {
            String implNames = "";
            for (EnvironmentImpl envImpl : residualImpls) {
                implNames += envImpl.getEnvironmentHome().toString() + " ";
            }

            /*
             * Clear the bad env state so that the next test is not
             * contaminated.
             */
            DbEnvPool.getInstance().clear();
            fail("residual environments:" + implNames);
        }
    }

    /**
     * Populates the master db without regard for the state of the replicas: It
     * uses ACK NONE to populate the database.
     */
    protected CommitToken populateDB(ReplicatedEnvironment rep,
                                     String dbName,
                                     int startKey,
                                     int nRecords)
        throws DatabaseException {

        Environment env = rep;
        Transaction txn =
            env.beginTransaction(null, RepTestUtils.SYNC_SYNC_NONE_TC);
        Database db = env.openDatabase(txn, dbName, dbconfig);
        txn.commit();
        txn = env.beginTransaction(null, RepTestUtils.SYNC_SYNC_NONE_TC);
        for (int i = 0; i < nRecords; i++) {
            IntegerBinding.intToEntry(startKey+i, key);
            LongBinding.longToEntry(i, data);
            db.put(txn, key, data);
        }
        txn.commit();
        db.close();
        return txn.getCommitToken();
    }

    protected CommitToken populateDB(ReplicatedEnvironment rep,
                                     String dbName,
                                     int nRecords)
        throws DatabaseException {

        return populateDB(rep, dbName, 0, nRecords);
    }

    protected void createGroup()
        throws UnknownMasterException,
               DatabaseException {

        createGroup(repEnvInfo.length);
    }

    protected void createGroup(int firstn)
        throws UnknownMasterException,
               DatabaseException {

        for (int i = 0; i < firstn; i++) {
            ReplicatedEnvironment rep = repEnvInfo[i].openEnv();
            State state = rep.getState();
            assertEquals((i == 0) ? State.MASTER : State.REPLICA, state);
        }
    }

    protected ReplicatedEnvironment leaveGroupAllButMaster()
        throws DatabaseException {

        ReplicatedEnvironment master = null;
        for (RepEnvInfo repi : repEnvInfo) {
            if (repi.getEnv() == null) {
                continue;
            }
            if (State.MASTER.equals(repi.getEnv().getState())) {
                master = repi.getEnv();
            } else {
                repi.closeEnv();
            }
        }
        assert(master!= null);
        return master;
    }

    /**
     * Restarts the nodes in an existing group. Returns the info associated
     * with the Master.
     *
     * @return the RepEnvInfo associated with the master, or null if there is
     * no master this could be because the election was not concluded in
     * JOIN_WAIT_TIME
     */
    protected RepEnvInfo restartNodes(RepEnvInfo... nodes)
        throws InterruptedException {

        /* Restart the group. */
        EnvOpenThread threads[] = new EnvOpenThread[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            threads[i]= new EnvOpenThread(nodes[i]);
            threads[i].start();
        }

        RepEnvInfo mi = null;

        for (EnvOpenThread eot : threads) {
            eot.join(JOIN_WAIT_TIME);
            if (eot.testException != null) {
                eot.testException.printStackTrace();
            }
            assertNull("test exception: " +
                       eot.testException, eot.testException);
            final ReplicatedEnvironment renv = eot.threadRepInfo.getEnv();
            if ((renv != null) &&
                renv.isValid() &&
                renv.getState().isMaster()) {
                mi = eot.threadRepInfo;
            }
        }
        return mi;
    }

    /**
     * Find and return the Master from the set of nodes passed in.
     */
    protected RepEnvInfo findMaster(RepEnvInfo... nodes) {
        for (RepEnvInfo ri : nodes) {
            if ((ri.getEnv() == null) || !ri.getEnv().isValid()) {
                continue;
            }
            if (ri.getEnv().getState().isMaster()) {
                return ri;
            }
        }
        return null;
    }

    /**
     * Close the nodes that were passed in. Close the master last to prevent
     * spurious elections, where intervening elections create Masters that are
     * immediately closed.
     */
    protected void closeNodes(RepEnvInfo... nodes) {
        RepEnvInfo mi = null;
        for (RepEnvInfo ri : nodes) {
            ReplicatedEnvironment env = ri.getEnv();
            if ((env == null) || !env.isValid()) {
                continue;
            }
            if (env.getState().isMaster()) {
                mi = ri;
                continue;
            }
            ri.closeEnv();
        }
        if (mi != null) {
            mi.closeEnv();
        }
    }
}
