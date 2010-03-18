/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReplicaReading.java,v 1.14 2010/01/12 06:21:49 tao Exp $
 */

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityIndex;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;

/**
 * Applications does reading operations on replica may cause reader transaction
 * deadlocks, since JE ReplayTxn would steal locks to make sure it can finish
 * its own work. Simulate such an application and measure how many retries the
 * reader transactions would do and check whether the log cleaning works as 
 * expected in HA.
 *
 * This test uses DPL and is divided into two phases: ramp up stage and steady 
 * stage. It's not a fail-over test, all replicas are alive during the test.
 *
 * Configurations
 * ==========================================================================
 * envRoot:     environment home root for the whole replication group, it's the
 *              same as we used in HA unit tests.
 * repNodeNum:  size of the replication group, default is 2.
 * dbSize:      number of records in the database, default is 200.
 * roundTraverse: master would traverse this number of databases and then it 
 *                would do a sync for the whole group, default is 200.
 * steadyOps:   number of updates the test would do before it finishes, default
 *              is 6,000,000.
 * txnOps:      number of operations the test wants to do protected by a
 *              transaction, default is 10.
 * nPriThreads: number of threads reading primary index, default is 2.
 * nSecThreads: number of threads reading secondary index, default is 2.
 *
 * Work Flow
 * ==========================================================================
 * During the ramp up stage, master will do "dbSize" insertions and sync whole
 * replication group.
 *
 * During the steady stage, the test would start "nPriThreads + nSecThreads" 
 * reading on replica, all of them read backwards. The reading threads can get
 * records from primary index and secondary index, and check the data 
 * correctness. 
 *
 * At the same time, the master would do "steadyOps" operations. It would first
 * delete the smallest "txnOps" records in the database, then do updates, and 
 * insert "txnOps" new records at the end of the database. After it traverses  
 * "roundTraverse" time on the database, the test would sync the whole group 
 * and check node equality. 
 *
 * After the "steadyOps" is used up, both the reading operations on the replica
 * and the update operations on master would stop. Then the test would close
 * all the replicas and check whether log cleaning does work in this test.
 *
 * How To Run This Test
 * ==========================================================================
 * All the test configurations have a default value, except the envRoot, so 
 * you need to assign a directory to "envRoot" to start the test, like:
 *    java ReplicaReading -envRoot data
 *
 * If you want to specify some configurations, please see the usage.
 */
public class ReplicaReading {
    /* Master of the replication group. */
    private ReplicatedEnvironment master;
    private RepEnvInfo[] repEnvInfo;
    private boolean runnable = true;
    /* The two variables saves the maximum and minimum reading retry number. */
    private int minNum = 100;
    private int maxNum = 0;
    /* The smallest and largest key in the database. */
    private int beginKey;
    private int endKey;
    /* Number of files deleted by Cleaner on each node. */
    private long[] fileDeletions;

    /* ----------------Configurable params-----------------*/    
    /* Environment home root for whole replication group. */
    private File envRoot;
    /* Replication group size. */
    private int nNodes = 2;
    /* Database size. */
    private int dbSize = 300;
    /* Steady state would finish after doing this number of operations. */
    private int steadyOps = 6000000;
    /* Do a sync after traversing the database these times. */ 
    private int roundTraverse = 200; 
    /* Transaction commits after doing this number of operations. */
    private int txnOps = 10;
    /* Thread number of reading PrimaryIndex on replica. */
    private int nPriThreads = 2;
    /* Thread number of reading SecondaryIndex on replica. */
    private int nSecThreads = 2;
    /* True if replica reading thread doing reverse reads. */
    private boolean isReverseRead = true;
    /* Size of each JE log file. */
    private String logFileSize = "5000000";
    /* Checkpointer wakes up when JE writes checkpointBytes bytes. */
    private String checkpointBytes = "10000000";

    public void doRampup() 
        throws Exception {

        repEnvInfo = 
            Utils.setupGroup(envRoot, nNodes, logFileSize, checkpointBytes);
        master = Utils.getMaster(repEnvInfo);
        fileDeletions = new long[nNodes];
        RepTestData.insertData(Utils.openStore(master, Utils.DB_NAME), dbSize);
        beginKey = 1;
        endKey = dbSize;
        Utils.doSyncAndCheck(repEnvInfo);
    }

    /* 
     * TODO: when replication mutable property is ready, need to test the two
     * nod replication.
     */
    public void doSteadyState() 
        throws Exception {

        /* The latch used to start all the threads at the same time. */
        CountDownLatch startSignal = new CountDownLatch(1);
        /* The latch used to stop the threads. */
        CountDownLatch endSignal = 
            new CountDownLatch(nPriThreads + nSecThreads);
        /* Start the threads. */
        startThreads(startSignal, endSignal, nPriThreads, false);
        startThreads(startSignal, endSignal, nSecThreads, true);
        /* Count down the latch, so that all threads start to work. */
        startSignal.countDown();
        /* Doing the updates. */
        doMasterUpdates();

        /* Print out the minimum and maximum retry number of this test. */
        if (Utils.VERBOSE) {
            System.out.println("The minimum retry number is: " + minNum);
            System.out.println("The maximum retry number is: " + maxNum);
        }

        /* 
         * Wait threads finish their job so that the test can close the 
         * environments without any open EntityStores.
         */
        endSignal.await();

        RepTestUtils.shutdownRepEnvs(repEnvInfo);
    }

    /* Start the reading threads. */
    private void startThreads(CountDownLatch startSignal, 
                              CountDownLatch endSignal,
                              int threadNum,
                              boolean secondary) {
        for (int i = 0; i < threadNum; i++) {
            Thread thread = new ReplicaReadingThread(repEnvInfo[1].getEnv(), 
                                                     startSignal, 
                                                     endSignal,
                                                     secondary);
            thread.start();
        }
    }

    /* Do the updates on master. */
    private void doMasterUpdates() 
        throws Exception {

        int txnRounds = 0;
        int tempTraverse = roundTraverse;
        boolean committed = false;

        EntityStore dbStore = Utils.openStore(master, Utils.DB_NAME);
        PrimaryIndex<Integer, RepTestData> primaryIndex = 
            dbStore.getPrimaryIndex(Integer.class, RepTestData.class);

        /* Make the steadyOps can be divided by txnOps. */
        steadyOps = (steadyOps % txnOps != 0) ? 
            steadyOps + (txnOps - (steadyOps % txnOps)) : steadyOps;

        Transaction txn = null;
        while (runnable) {
            int tempEndKey = endKey;
            boolean doDeletion = true;
            boolean doInsertion = false;
            if (Utils.VERBOSE) {
                System.out.println("master " + steadyOps);
            }
            for (int i = beginKey; i <= tempEndKey; i++) {
                /* Create a new transaction for every txnOps operations. */
                if ((i - 1) % txnOps == 0) {
                    committed = false;
                    txn = master.beginTransaction(null, null);
                    txnRounds++;
                }
                if (doDeletion) {
                    primaryIndex.delete(txn, i);
                } else {
                    RepTestData data = new RepTestData();
                    if (!doInsertion) {
                        data.setKey(i);
                    }
                    data.setData(i);
                    data.setName("test" + new Integer(txnRounds).toString());
                    primaryIndex.put(txn, data);
                }
                if ((i % txnOps == 0) || (i == tempEndKey)) {
                    txn.commit();
                    committed = true;
                    if (i == (beginKey + txnOps - 1) && doDeletion) {
                        doDeletion = false;
                        beginKey += txnOps;
                    }
                    if (i == tempEndKey) {
                        if (doInsertion) {
                            endKey += txnOps;
                            doInsertion = false;
                        } else {
                            tempEndKey += txnOps;
                            doInsertion = true;
                        }
                    }
                }

                if (--steadyOps == 0) {
                    if (!committed) {
                        txn.commit();
                    }
                    runnable = false;
                    break;
                }
            }
            if (--tempTraverse == 0 || !runnable) {
                dbStore.close();
                Utils.doSyncAndCheck
                    (RepTestUtils.getOpenRepEnvs(repEnvInfo));
                if (runnable) {
                    dbStore = Utils.openStore(master, Utils.DB_NAME);
                    primaryIndex = dbStore.getPrimaryIndex(Integer.class, 
                                                           RepTestData.class);
                }
                tempTraverse = roundTraverse;
            }
        }
    }

    protected void parseArgs(String args[]) 
        throws Exception {

        for (int i = 0; i < args.length; i++) {
            boolean moreArgs = i < args.length - 1;
            if (args[i].equals("-h") && moreArgs) {
                envRoot = new File(args[++i]);
            } else if (args[i].equals("-repNodeNum") && moreArgs) {
                nNodes = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-dbSize") && moreArgs) {
                dbSize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-steadyOps") && moreArgs) {
                steadyOps = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-roundTraverse") && moreArgs) {
                roundTraverse = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-txnOps") && moreArgs) {
                txnOps = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-logFileSize") && moreArgs) {
                logFileSize = args[++i];
            } else if (args[i].equals("-checkpointBytes") && moreArgs) {
                checkpointBytes = args[++i];
            } else if (args[i].equals("-nPriThreads") && moreArgs) {
                nPriThreads = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-nSecThreads") && moreArgs) {
                nSecThreads = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-isReverseRead") && moreArgs) {
                isReverseRead = Boolean.parseBoolean(args[++i]);
            } else {
                usage("Unknown arg: " + args[i]);
            }
        }

        if (nNodes < 2) {
            throw new IllegalArgumentException
                ("Replication group size should > 2!");
        }

        if (txnOps >= dbSize || dbSize % txnOps != 0) {
            throw new IllegalArgumentException
                ("dbSize should be larger and integral multiple of txnOps!");
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
             "     [-steadyOps <the total update operations in steady " +
             "state>]\n" +
             "     [-roundTraverse <do a sync in the replication group " + 
             "after traversing the database of this number]\n" +
             "     [-txnOps <number of operations in each transaction>]\n" +
             "     [-logFileSize <size of each log file>]\n" +
             "     [-checkpointBytes <checkpointer wakes up after writing " +
             "these bytes into the on disk log>]\n" +
             "     [-nPriThreads <number of threads reading PrimaryIndex " +
             "on replica>]\n" +
             "     [-nSecThreads <number of threads reading SecondaryIndex " +
             "on replica>]\n" +
             "     [-isReverseRead <true if replica reading threads read " +
             "backwards>]");
        System.exit(2);
    }

    public static void main(String args[]) {
        try {
            ReplicaReading test = new ReplicaReading();
            test.parseArgs(args);
            test.doRampup();
            test.doSteadyState();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /* The reading thread on replica. */
    class ReplicaReadingThread extends Thread {
        private final ReplicatedEnvironment repEnv;
        private final CountDownLatch startSignal;
        private final CountDownLatch endSignal;
        private boolean secondary;
        private final ArrayList<RepTestData> list;

        public ReplicaReadingThread(ReplicatedEnvironment repEnv, 
                                    CountDownLatch startSignal,
                                    CountDownLatch endSignal,
                                    boolean secondary) {
            this.repEnv = repEnv;
            this.startSignal = startSignal;
            this.endSignal = endSignal;
            this.secondary = secondary;
            list = new ArrayList<RepTestData>();
        }

        public void run() {
            try {
                startSignal.await();

                EntityStore dbStore = Utils.openStore(repEnv, Utils.DB_NAME);
                EntityIndex<Integer, RepTestData> index =  
                    dbStore.getPrimaryIndex(Integer.class, RepTestData.class);
                if (secondary) {
                    index = dbStore.getSecondaryIndex((PrimaryIndex) index, 
                                                      Integer.class, 
                                                      "data");
                }
                
                while (runnable) {
                    int numIters = (endKey - beginKey) / txnOps;
                    int startKey = beginKey;
                    /* Do txnOps read operations during each transaction. */
                    for (int i = 0; runnable && i < numIters; i++) {
                        int start = i * txnOps + startKey;
                        int end = start + txnOps - 1;

                        /* 
                         * If there is no data between start and end, then 
                         * break and get a new start and end. 
                         */
                        if (doRetries(start, end, repEnv, index)) {
                            break;
                        }
                    }
                }
                dbStore.close();
                endSignal.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /* Retry if there exists deadlock. */
        private boolean doRetries(int start,
                                  int end,
                                   ReplicatedEnvironment repEnv, 
                                   EntityIndex<Integer, RepTestData> index) {
            boolean success = false;
            boolean noData = false;
            int maxTries = 100;

            for (int tries = 0; !success && tries < maxTries; tries++) {
                try {
                    Transaction txn = repEnv.beginTransaction(null, null);
                    int realStart = 0;
                    EntityCursor<RepTestData> cursor = null;
                    try {
                        cursor = index.entities(txn, null);
                        realStart = cursor.first(null).getKey();
                        cursor.close();
                        cursor = 
                            index.entities(txn, start, true, end, true, null);
                        noData = addRecordsToList(cursor);
                        success = true;
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }

                        if (success) {
                            if (noData) {
                                checkNoDataCorrectness(start, realStart, tries);
                            } else {
                                checkCorrectness(tries);
                            }
                            txn.commit();
                        } else {
                            txn.abort();
                        }
                        list.clear();
                    }
                } catch (LockConflictException e) {
                    success = false;
                }
            }

            return noData;
        }

        /* 
         * If there is no data in this cursor, return true. If there exists
         * data in the cursor, add the datas into the list and return false.
         */
        private boolean addRecordsToList(EntityCursor<RepTestData> cursor) 
            throws DatabaseException {

            if (isReverseRead) {
                RepTestData data = cursor.last(null);
                if (data == null) {
                    return true;
                } else {
                    list.add(data);
                    while ((data = cursor.prev(null)) != null) {
                        list.add(data);
                    }
                }
            } else {
                RepTestData data = cursor.first(null);
                if (data == null) {
                    return true;
                } else {
                    list.add(data);
                    while ((data = cursor.next(null)) != null) {
                        list.add(data);
                    }
                }
            }

            return false;
        }

        /* Check the correctness if there is no data in the cursor. */
        private void checkNoDataCorrectness(int start,
                                            int realStart,
                                            int tries) {
            /* Expect the list size to 0. */
            if (list.size() != 0) {
                System.err.println("The expected number of records should " +
                                   "be 0, but it is " + list.size() + "!");
                System.exit(-1);
            }

            /* 
             * The actual beginKey should be larger than the specified 
             * beginKey, and the distance between them should be integral
             * multiple of txnOps.
             */
            if (realStart < start || ((realStart - start) % txnOps != 0)) {
                System.err.println("There are some deleted key exists in " +
                                   "database!");
                System.err.println("Expected start key is: " + start + 
                                   ", real start key is: " + realStart);
                System.exit(-1);
            } 
            updateRetries(tries);
        }

        private void checkCorrectness(int tries) {
            if (list.size() == txnOps) {
                int minus = isReverseRead ? 1 : -1;
                RepTestData firstData = list.get(0);
                for (int i = 0; i < list.size(); i++) {
                    if (!firstData.logicEquals(list.get(i), i * minus)) {
                        System.err.println("Reading data is wrong!" +
                                           "FirstData: " + firstData + 
                                           "WrontData: " + list.get(i));
                        for (RepTestData each : list) {
                            System.err.println(each);
                        }
                        System.exit(-1);
                    }
                }
                updateRetries(tries);
            } else {
                System.err.println("The expected number of records should " +
                                   "be: " + txnOps + ", but it is " +
                                   list.size() + "!");
                System.exit(-1);
            }
        }

        private void updateRetries(int tries) {
            /* Assign the value to maxNum and minNum. */
            synchronized(this) {
                maxNum = maxNum < tries ? tries : maxNum;
                minNum = minNum < tries ? minNum : tries;
            }
            if (tries > 0 && Utils.VERBOSE) {
                System.err.println("Retries this round: " + tries);
            }
        }
    }
}
