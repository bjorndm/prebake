/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: CleanerThrottleTest.java,v 1.7 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl.node;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Durability;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.VLSN;

/**
 * Test that the cleaner will is throttled by the CBVLSN.
 */
public class CleanerThrottleTest extends TestCase {

    private final boolean verbose = Boolean.getBoolean("verbose");
    private final int heartbeatMS = 500;

    /* Replication tests use multiple environments. */
    private final File envRoot;

    public CleanerThrottleTest() {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * All nodes running, no pad, should clean a lot.
     */
    public void testAllNodesClean()
        throws Throwable {

        runAndClean(0,      // pad 
                    11,     // numDeletedFiles,
                    false); // killOneNode
    }

    /**
     * All nodes running, pad of 100, should clean, but less.
     */
    public void testAllNodesCleanWithPad()
        throws Throwable {

        runAndClean(100,    // pad 
                    7,      // numDeletedFiles,
                    false); // killOneNode
    }

    /**
     * One node from a three node group is killed off, should gate any
     * cleaning.
     */
    public void testTwoNodesClean()
        throws Throwable {

        runAndClean(0,     // pad 
                    0,     // numDeletedFiles,
                    true); // killOneNode
    }

    /**
     * Create 3 nodes and replicate operations.
     * Kill off the master, and make the other two resume. This will require
     * a syncup and a rollback of any operations after the matchpoint.
     */
    public void runAndClean(int pad, int numDeletedFiles, boolean killOneNode) 
        throws Throwable {

        RepEnvInfo[] repEnvInfo = null;

        EnvironmentConfig smallFileConfig = new EnvironmentConfig();
        DbInternal.disableParameterValidation(smallFileConfig);
        /* Use uniformly small log files, to permit cleaning.  */
        smallFileConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, "2000");
        smallFileConfig.setAllowCreate(true);
        smallFileConfig.setTransactional(true);
        /* Turn off the cleaner so we can call cleanLog explicitly. */
        smallFileConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, 
                                       "false");
        smallFileConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, 
                                       "false");
        ReplicationConfig repConfig = new ReplicationConfig();
        RepInternal.disableParameterValidation(repConfig);
        repConfig.setConfigParam(RepParams.CBVLSN_PAD.getName(),
                                 (new Integer(pad)).toString());
        repConfig.setConfigParam(RepParams.HEARTBEAT_INTERVAL.getName(),
                                 (new Integer(heartbeatMS)).toString());

        try {
            /* Create a 3 node group */
            repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 
                                                    3,
                                                    smallFileConfig,
                                                    repConfig);
            ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
            
            if (killOneNode) {
                for (RepEnvInfo repi : repEnvInfo) {
                    if (repi.getEnv() != master) {
                        repi.closeEnv();
                        break;
                    }
                }
            }

            /* Run a workload that will create cleanable waste. */
            doWastefulWork(master);

            VLSNIndex vlsnIndex =
                RepInternal.getRepImpl(master).getVLSNIndex();
            VLSNRange range = vlsnIndex.getRange();
            VLSN lastVLSN = range.getLast();
            RepTestUtils.syncGroupToVLSN(repEnvInfo, 
                                         (killOneNode ? 
                                          repEnvInfo.length-1 :
                                          repEnvInfo.length),
                                         lastVLSN);
            Thread.sleep(heartbeatMS * 3);
            lastVLSN = vlsnIndex.getRange().getLast();
            /* Run cleaning on each node. */
            for (RepEnvInfo repi : repEnvInfo) {
                cleanLog(repi.getEnv(), numDeletedFiles, killOneNode);
            }

            /* Check VLSN index */
            for (RepEnvInfo repi : repEnvInfo) {
                    if (repi.getEnv() == null) {
                            continue;
                    }
                vlsnIndex = 
                    RepInternal.getRepImpl(repi.getEnv()).getVLSNIndex();
                range = vlsnIndex.getRange();
                if (verbose) {
                    System.out.println("rangefirst=" + range.getFirst() +
                                       " lastVLSN=" + lastVLSN);
                }
                assertTrue(lastVLSN.compareTo(range.getFirst()) >= 0);
                assertTrue((lastVLSN.getSequence() -
                            range.getFirst().getSequence()) >= pad);
                            
            }
        } catch(Exception e) {
                e.printStackTrace();
                throw e;
        } finally {
            for (RepEnvInfo repi : repEnvInfo) {
                if (repi.getEnv() != null) {
                    repi.closeEnv();
                }
            }
        }
    }
    
    private void doWastefulWork(ReplicatedEnvironment master) {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = master.openDatabase(null, "test", dbConfig);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[100]);

        TransactionConfig txnConfig = new TransactionConfig();
        txnConfig.setDurability(new Durability(SyncPolicy.NO_SYNC,
                                               SyncPolicy.NO_SYNC,
                                               ReplicaAckPolicy.SIMPLE_MAJORITY));
        try {
            for (int i = 0; i < 100; i++) {
                IntegerBinding.intToEntry(i, key);
                Transaction txn = master.beginTransaction(null, txnConfig);
                db.put(txn, key, data);
                db.delete(txn, key);
                txn.commit();
            }
            
            /* One more synchronous one to flush the log files. */
            IntegerBinding.intToEntry(101, key);
            txnConfig.setDurability(new Durability(SyncPolicy.SYNC,
                                                   SyncPolicy.SYNC,
                                                   ReplicaAckPolicy.SIMPLE_MAJORITY));
            Transaction txn = master.beginTransaction(null, txnConfig);
            db.put(txn, key, data);
            db.delete(txn, key);
            txn.commit();
        } finally {
            db.close();
        } 
    }

    private void cleanLog(ReplicatedEnvironment repEnv,
                          int minNumberOfDeletedFiles,
                          boolean killOneNode) {
            if (repEnv == null) {
                    return;
            }
            
        CheckpointConfig force = new CheckpointConfig();
        force.setForce(true);
        
        EnvironmentStats stats = repEnv.getStats(new StatsConfig());
        int numCleaned = 0;
        int cleanedThisRun = 0;
        long beforeNFileDeletes = stats.getNCleanerDeletions();
        while ((cleanedThisRun = repEnv.cleanLog()) > 0) {
            numCleaned += cleanedThisRun;
        }
        repEnv.checkpoint(force);
        
        while ((cleanedThisRun = repEnv.cleanLog()) > 0) {
            numCleaned += cleanedThisRun;
        }
        repEnv.checkpoint(force);

        if (verbose) {
            System.out.println("cleanedFiles = " + numCleaned);
        }

        stats = repEnv.getStats(new StatsConfig());        
        long afterNFileDeletes = stats.getNCleanerDeletions();
        long actualDeleted = afterNFileDeletes - beforeNFileDeletes;

        if (verbose) {
            System.out.println(repEnv.getNodeName() +
                               " cbvlsn=" +
                               RepInternal.getRepImpl(repEnv).
                               getRepNode().getGroupCBVLSN() +
                               " deleted files = " + actualDeleted +
                               " numCleaned=" + numCleaned);
        }

        if (killOneNode) {
            assertEquals(0, actualDeleted); 
        } else {
            assertTrue("actualDeleted = " + actualDeleted +
                               " min=" + minNumberOfDeletedFiles,
                               (actualDeleted >= minNumberOfDeletedFiles));
        }
    }
}
