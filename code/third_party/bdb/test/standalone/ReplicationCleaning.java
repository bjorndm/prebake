/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReplicationCleaning.java,v 1.7 2010/01/04 15:51:12 cwl Exp $
 */

import java.io.File;
import java.util.Random;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class ReplicationCleaning {
    /* Master of the replication group. */
    private ReplicatedEnvironment master;
    private RepEnvInfo[] repEnvInfo;
    /* Number of files deleted by Cleaner on each node. */
    private long[] fileDeletions;
    /* Used for generating random keys. */
    private final Random random = new Random();
    /* Configuration used when get EnvironmentStats. */
    private StatsConfig statsConfig;

    /* -------------------Configurable params----------------*/
    /* Environment home root for whole replication group. */
    private File envRoot;
    /* Replication group size. */
    private int nNodes = 3;
    /* Database size. */
    private int dbSize = 2000;
    /* Steady state would finish after doing this number of operations. */
    private int steadyOps = 6000000;
    /* Size of each JE log file. */
    private String logFileSize = "409600";
    /* Checkpointer wakes up when JE writes checkpointBytes bytes. */
    private String checkpointBytes = "1000000";
    /* Select a new master after doing this number of operations. */ 
    private int roundOps = 60000; 

    public void doRampup() 
        throws Exception {

        statsConfig = new StatsConfig();
        statsConfig.setFast(false);
        statsConfig.setClear(true);

        repEnvInfo = 
            Utils.setupGroup(envRoot, nNodes, logFileSize, checkpointBytes);
        master = Utils.getMaster(repEnvInfo);
        fileDeletions = new long[nNodes];
        RepTestData.insertData(Utils.openStore(master, Utils.DB_NAME), dbSize);
        Utils.doSyncAndCheck(repEnvInfo);
    }

    /* 
     * TODO: when replication mutable property is ready, need to test the two
     * nod replication.
     */
    public void doSteadyState() 
        throws Exception {

        /* Used to check whether steadyOps is used up. */
        int round = 0;

        while (true) {
            round++;
                
            /*
             * Shutting down the current master, let the remaining nodes vote,
             * and then do updates.
             */
            int masterId = RepInternal.getNodeId(master);
            shutdownMaster(masterId - 1);
            if (nNodes != 2) {
                master = Utils.getMaster
                    (RepTestUtils.getOpenRepEnvs(repEnvInfo));
            } else {
                master = Utils.assignMaster(repEnvInfo, masterId, false);
            }

            /* 
             * If doWork returns false, it means the steadyOps is used up, so
             * break the loop.
             */
            if (!doWork(round)) {
                break;
            }
            /* Re-open the closed nodes and have them re-join the group. */
            if (nNodes != 2) {
                master = Utils.getMaster(repEnvInfo);
            } else {
                master = Utils.assignMaster(repEnvInfo, masterId, true);
            }
            Utils.doSyncAndCheck(repEnvInfo);
        }

        /* 
         * Re-open the closed nodes and have them re-join the group. And do a 
         * sync here since the test exits the while loop without doing a sync. 
         */
        master = Utils.getMaster(repEnvInfo);
        Utils.doSyncAndCheck(repEnvInfo);
    
        /* Close the environment and check the log cleaning. */
        Utils.closeEnvAndCheckLogCleaning(repEnvInfo, fileDeletions, true);
    }

    /* 
     * Shutdown master and save how many files are deleted by the Cleaner on 
     * this replicator in this round.
     */ 
    private void shutdownMaster(int masterId)
        throws DatabaseException {

        if (Utils.VERBOSE) {
            System.err.println("Closing master: " + (masterId + 1));
        }

        /* Save the nCleanerDeletions stat on this replicator. */
        fileDeletions[masterId] = fileDeletions[masterId] + 
            repEnvInfo[masterId].getEnv().
            getStats(statsConfig).getNCleanerDeletions();
        if (Utils.VERBOSE) {
            System.err.println("File deletions on master " + (masterId + 1) +
                    ": " + fileDeletions[masterId]);
        }
        repEnvInfo[masterId].closeEnv();
    }

    /* Return false if the steadyOps is used up. */
    private boolean doWork(int round) 
        throws Exception {

        boolean runAble = true;

        EntityStore dbStore = Utils.openStore(master, Utils.DB_NAME);
        PrimaryIndex<Integer, RepTestData> primaryIndex = 
            dbStore.getPrimaryIndex(Integer.class, RepTestData.class);

        for (int i = 0; i < roundOps; i++) {
            /* Do a random update here. */
            int key = random.nextInt(dbSize);
            RepTestData data = new RepTestData();
            data.setKey(key);
            data.setData(round * roundOps + i);
            data.setName("test" + (new Integer(key)).toString()); 
            primaryIndex.put(data);

            /* Check whether the steady stage should break. */
            if (--steadyOps == 0) {
                runAble = false;
                break;
            }
        }
        dbStore.close();

        Utils.doSyncAndCheck(RepTestUtils.getOpenRepEnvs(repEnvInfo));

        return runAble;
    }

    public void parseArgs(String args[]) 
        throws Exception {

        for (int i = 0; i < args.length; i++) {
            boolean moreArgs = i < args.length - 1;
            if (args[i].equals("-h") && moreArgs) {
                envRoot = new File(args[++i]);
            } else if (args[i].equals("-repNodeNum") && moreArgs) {
                nNodes = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-dbSize") && moreArgs) {
                dbSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-logFileSize") && moreArgs) {
                logFileSize = args[++i];
            } else if (args[i].equals("-steadyOps") && moreArgs) {
                steadyOps = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-roundOps") && moreArgs) {
                roundOps = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-checkpointBytes") && moreArgs) {
                checkpointBytes = args[++i];
            } else {
                usage("Unknown arg: " + args[i]);
            }
        }

        if (nNodes < 2) {
            throw new IllegalArgumentException
                ("Replication group size should > 2!");
        }

        if (steadyOps < roundOps) {
            throw new IllegalArgumentException
                ("steadyOps should be larger than roundOps!");
        }
    }

    private void usage(String error) {
        if (error != null) {
            System.err.println(error);
        }
        System.err.println
            ("java " + getClass().getName() + "\n" +
             "     [-h <replication group Environment home dir>]\n" +
             "     [-repNodeNum <replication group size>]\n" +
             "     [-dbSize <records' number of the tested database>]\n" +
             "     [-logFileSize <JE log file size>]\n" +
             "     [-checkpointBytes <Checkpointer wakeup interval bytes>]\n" +
             "     [-steadyOps <the total update operations in steady state>]\n" +
             "     [-roundOps <select a new master after running this " + 
             "number of operations>]\n" +
             "     [-forceCheckpoint <true if invoke Checkpointer " +
             "explicitly>]\n");
        System.exit(2);
    }

    public static void main(String args[]) {
        try {
            ReplicationCleaning test = new ReplicationCleaning();
            test.parseArgs(args);
            test.doRampup();
            test.doSteadyState();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
