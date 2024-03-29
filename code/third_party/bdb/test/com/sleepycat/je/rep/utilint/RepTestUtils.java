/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepTestUtils.java,v 1.15 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.rep.utilint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.je.CommitToken;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.ReplicaConsistencyPolicy;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.cleaner.VerifyUtils;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.rep.CommitPointConsistencyPolicy;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.QuorumPolicy;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.UnknownMasterException;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.impl.PointConsistencyPolicy;
import com.sleepycat.je.rep.impl.RepGroupDB;
import com.sleepycat.je.rep.impl.RepGroupImpl;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.RepGroupDB.NodeBinding;
import com.sleepycat.je.rep.impl.networkRestore.NetworkBackup;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.stream.FeederReader;
import com.sleepycat.je.rep.stream.OutputWireRecord;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.util.DbBackup;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.VLSN;

/**
 * Static utility methods and instances for replication unit tests.
 *
 * Examples of useful constructs here are methods that:
 * <ul>
 * <li>Create multiple environment directories suitable for unit testing
 * a set of replicated nodes.
 * <li>Create a router config that is initialized with exception and event
 * listeners that will dump asynchronous exceptions to stderr, and which
 * can be conditionalized to ignore exceptions at certain points when the
 * test expects a disconnected node or other error condition.
 * <li>Methods that compare two environments to see if they have a common
 * replication stream.
 * <li>etc ...
 * </ul>
 */
public class RepTestUtils {

    public static final String TEST_HOST = "localhost";
    private static final String REPDIR = "rep";
    public static final String TEST_REP_GROUP_NAME = "UnitTestGroup";

    /*
     * If -DoverridePort=<val> is set, then replication groups will be
     * set up with this default port value.
     */
    public static final String OVERRIDE_PORT = "overridePort";

    /*
     * If -DlongTimeout is true, then this test will run with very long
     * timeouts, to make interactive debugging easier.
     */
    private static final boolean longTimeout =
        Boolean.getBoolean("longTimeout");

    public static final int MINUTE_MS = 60*1000;

    /* Time to wait for each node to start up and join the group. */
    private static final long JOIN_WAIT_TIME = 20000;

    /* The basis for varying log file size */
    private static int envCount = 1;

    /* Convenient constants */
    public final static TransactionConfig SYNC_SYNC_ALL_TC =
        new TransactionConfig();

    public final static TransactionConfig SYNC_SYNC_NONE_TC =
        new TransactionConfig();

    public final static Durability SYNC_SYNC_ALL_DURABILITY =
        new Durability(Durability.SyncPolicy.SYNC,
                       Durability.SyncPolicy.SYNC,
                       Durability.ReplicaAckPolicy.ALL);

    public final static Durability SYNC_SYNC_NONE_DURABILITY =
        new Durability(Durability.SyncPolicy.SYNC,
                       Durability.SyncPolicy.SYNC,
                       Durability.ReplicaAckPolicy.NONE);

    public static final Durability DEFAULT_DURABILITY =
        new Durability(Durability.SyncPolicy.WRITE_NO_SYNC,
                       Durability.SyncPolicy.WRITE_NO_SYNC,
                       Durability.ReplicaAckPolicy.SIMPLE_MAJORITY);
    static {
        SYNC_SYNC_ALL_TC.setDurability(SYNC_SYNC_ALL_DURABILITY);
        SYNC_SYNC_NONE_TC.setDurability(SYNC_SYNC_NONE_DURABILITY);
    }

    public static File[] getRepEnvDirs(File envRoot, int nNodes) {
        File envDirs[] = new File[nNodes];
        for (int i=0; i < nNodes; i++) {
            envDirs[i] = new File(envRoot, RepTestUtils.REPDIR + i);
        }
        return envDirs;
    }

    /**
     * Create nNode directories within the envRoot directory nodes, for housing
     * a set of replicated environments. Each directory will be named
     * <envRoot>/rep#, i.e <envRoot>/rep1, <envRoot>/rep2, etc.
     */
    public static File[] makeRepEnvDirs(File envRoot, int nNodes)
        throws IOException{

        File[] envHomes = new File[nNodes];
        File jeProperties = new File(envRoot, "je.properties");
        for (int i = 0; i < nNodes; i++) {
            envHomes[i] = new File(envRoot, REPDIR + i);
            envHomes[i].mkdir();

            /* Copy the test je.properties into the new directories. */
            File repProperties = new File (envHomes[i], "je.properties");
            FileInputStream from = null;
            FileOutputStream to = null;
            try {
                try {
                    from = new FileInputStream(jeProperties);
                } catch (FileNotFoundException e) {
                    jeProperties.createNewFile();

                    from = new FileInputStream(jeProperties);
                }
                to = new FileOutputStream(repProperties);
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = from.read(buffer)) != -1) {
                    to.write(buffer, 0, bytesRead);
                }
            } finally {
                if (from != null) {
                    try {
                        from.close();
                    } catch (IOException ignore) {
                    }
                }
                if (to != null) {
                    try {
                        to.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        return envHomes;
    }

    /**
     * Remove all the log files in the <envRoot>/rep* directories directory.
     */
    public static void removeRepEnvironments(File envRoot) {
        File[] repEnvs = envRoot.listFiles();
        for (File repEnv : repEnvs) {
            if (!repEnv.isDirectory()) {
                continue;
            }
            TestUtils.removeLogFiles("RemoveRepEnvironments",
                                     repEnv,
                                     false); // checkRemove
            new File(repEnv, "je.lck").delete();
            removeBackupFiles(repEnv);
        }
        TestUtils.removeLogFiles("RemoveRepEnvironments", envRoot, false);
        new File(envRoot, "je.lck").delete();

        removeBackupFiles(envRoot);
    }

    private static void removeBackupFiles(File repEnv) {
        for (File f : repEnv.listFiles(new NetworkBackup.BUPFilter())) {
            f.delete();
        }
    }

    /**
     * Create an array of environments, with basically the same environment
     * configuration.
     */

    public static RepEnvInfo[] setupEnvInfos(File envRoot, int nNodes)
        throws IOException {

        return setupEnvInfos(envRoot, nNodes, DEFAULT_DURABILITY);
    }

    /**
     * Fill in an array of environments, with basically the same environment
     * configuration. Only fill in the array slots which are null. Used to
     * initialize semi-populated set of environments.
     * @throws IOException
     */
    public static RepEnvInfo[] setupEnvInfos(File envRoot,
                                            int nNodes,
                                            Durability envDurability)
        throws IOException {

        File[] envHomes = makeRepEnvDirs(envRoot, nNodes);
        RepEnvInfo[] repEnvInfo = new RepEnvInfo[envHomes.length];

        for (int i = 0; i < repEnvInfo.length; i++) {
            if (repEnvInfo[i] == null) {
                repEnvInfo[i] = setupEnvInfo(envHomes[i],
                                             envDurability,
                                             (short) (i + 1), // nodeId
                                             repEnvInfo[0]);
            }
        }
        return repEnvInfo;
    }

    public static RepEnvInfo[] setupEnvInfos(File envRoot,
                                             int nNodes,
                                             EnvironmentConfig envConfig)
        throws IOException {

        return setupEnvInfos(envRoot, nNodes, envConfig, null);
    }

    public static RepEnvInfo[] setupEnvInfos(File envRoot,
                                             int nNodes,
                                             EnvironmentConfig envConfig,
                                             ReplicationConfig repConfig)
        throws IOException {

        File[] envdirs = makeRepEnvDirs(envRoot, nNodes);
        RepEnvInfo[] repEnvInfo  = new RepEnvInfo[envdirs.length];

        for (int i = 0; i < repEnvInfo.length; i++) {
            if (repEnvInfo[i] == null) {
                ReplicationConfig useRepConfig = null;
                if (repConfig == null) {
                    useRepConfig = createRepConfig(i + 1);
                } else {
                    useRepConfig = createRepConfig(repConfig, i + 1);
                }
                EnvironmentConfig useEnvConfig = envConfig.clone();
                repEnvInfo[i] = setupEnvInfo(envdirs[i],
                                             useEnvConfig,
                                             useRepConfig,
                                             repEnvInfo[0]);
            }
        }
        return repEnvInfo;
    }

    /**
     * Create info for a single replicated environment.
     */
    public static RepEnvInfo setupEnvInfo(File envHome,
                                          Durability envDurability,
                                          int nodeId,
                                          RepEnvInfo helper) {

        EnvironmentConfig envConfig = createEnvConfig(envDurability);
        return setupEnvInfo(envHome, envConfig, nodeId, helper);
    }

    /**
     * Create info for a single replicated environment.
     */
    public static RepEnvInfo setupEnvInfo(File envHome,
                                          EnvironmentConfig envConfig,
                                          int nodeId,
                                          RepEnvInfo helper) {
        return setupEnvInfo(envHome,
                            envConfig,
                            createRepConfig(nodeId),
                            helper);
    }

    /**
     * Create info for a single replicated environment.
     */
    public static RepEnvInfo setupEnvInfo(File envHome,
                                          EnvironmentConfig envConfig,
                                          ReplicationConfig repConfig,
                                          RepEnvInfo helper) {

        /*
         * Give all the environments the same environment configuration.
         *
         * If the file size is not set by the calling test, stagger their log
         * file length to give them slightly different logs and VLSNs. Disable
         * parameter validation because we want to make the log file length
         * smaller than the minimums, for testing.
         */
        if (!envConfig.isConfigParamSet(EnvironmentConfig.LOG_FILE_MAX)) {
            DbInternal.disableParameterValidation(envConfig);
            /*  Vary the file size */
            long fileLen = ((envCount++ % 100)+1) * 10000;
            envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX,
                                     Long.toString(fileLen));
        }

        repConfig.setHelperHosts((helper == null) ?
                                 repConfig.getNodeHostPort() :
                                 helper.getRepConfig().getNodeHostPort());

        /*
         * If -DlongTimeout is true, then this test will run with very long
         * timeouts, to make interactive debugging easier.
         */
        if (longTimeout) {
            setLongTimeouts(repConfig);
        }

        /*
         * If -DlongAckTimeout is true, then the test will set the
         * REPLICA_TIMEOUT to 50secs.
         */
        if (Boolean.getBoolean("longAckTimeout")) {
            repConfig.setReplicaAckTimeout(50, TimeUnit.SECONDS);
        }
        return new RepEnvInfo(envHome, repConfig, envConfig);
    }

    public static EnvironmentConfig createEnvConfig(Durability envDurability) {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setDurability(envDurability);

        return envConfig;
    }

    /**
     * Create a test RepConfig for the node with the specified id. Note that
     * the helper is not configured.
     */
    public static ReplicationConfig createRepConfig(int nodeId)
        throws NumberFormatException, IllegalArgumentException {

        return createRepConfig(new ReplicationConfig(), nodeId);
    }

    private static int getDefaultPort() {
        return Integer.getInteger
            (OVERRIDE_PORT,
             Integer.parseInt(RepParams.DEFAULT_PORT.getDefault()));
    }

    /**
     * Create a test RepConfig for the node with the specified id, using the
     * specified repConfig. The repConfig may have other parameters set
     * already. Note that the helper is not configured.
     */
    private static
        ReplicationConfig createRepConfig(ReplicationConfig repConfig,
                                          int nodeId)
        throws NumberFormatException, IllegalArgumentException {

        ReplicationConfig filledInConfig = repConfig.clone();

        final int firstPort = getDefaultPort();
        filledInConfig.setConfigParam
            (RepParams.ENV_SETUP_TIMEOUT.getName(), "60 s");
        filledInConfig.setConfigParam
            (ReplicationConfig.ENV_CONSISTENCY_TIMEOUT, "60 s");
        filledInConfig.setGroupName(TEST_REP_GROUP_NAME);
        filledInConfig.setNodeName("Node " + nodeId +"");
        String nodeHost = TEST_HOST + ":" + (firstPort + (nodeId - 1));
        filledInConfig.setNodeHostPort(nodeHost);
        return filledInConfig;
    }

    /**
     * Set timeouts to long intervals for debugging interactively
     */
    public static void setLongTimeouts(ReplicationConfig repConfig) {

        RepInternal.disableParameterValidation(repConfig);

        /* Wait an hour for this node to join the group.*/
        repConfig.setConfigParam(RepParams.ENV_SETUP_TIMEOUT.getName(),
                                 "1 h");
        repConfig.setConfigParam(ReplicationConfig.ENV_CONSISTENCY_TIMEOUT,
                                 "1 h");

        /* Wait an hour for replica acks. */
        repConfig.setConfigParam(ReplicationConfig.REPLICA_ACK_TIMEOUT,
                                 "1 h");

        /* Have a heartbeat every five minutes. */
        repConfig.setConfigParam(RepParams.HEARTBEAT_INTERVAL.getName(),
                                 "5 min");
    }

    /**
     * Shuts down the environments with a checkpoint at the end.
     *
     * @param repEnvInfo the environments to be shutdown
     */
    public static void shutdownRepEnvs(RepEnvInfo[] repEnvInfo) {

        shutdownRepEnvs(repEnvInfo, true);
    }

    /**
     * Shut down the environment, with an optional checkpoint. It sequences the
     * shutdown so that all replicas are shutdown before the master.  This
     * sequencing avoids side-effects in the tests where shutting down the
     * master first results in elections and one of the "to be shutdown"
     * replicas becomes a master and so on.
     *
     * @param repEnvInfo the environments to be shutdown
     *
     * @param doCheckpoint whether do a checkpoint at the end of close
     */
    public static void shutdownRepEnvs(RepEnvInfo[] repEnvInfo,
                                       boolean doCheckpoint) {

        if (repEnvInfo == null) {
            return;
        }

        ReplicatedEnvironment master = null;
        for (RepEnvInfo ri : repEnvInfo) {
            if ((ri.repEnv == null) || RepInternal.isClosed(ri.repEnv)) {
                continue;
            }
            if (ri.repEnv.getState().isMaster()) {
                if (master != null) {
                    throw new IllegalStateException
                        ("Multiple masters: " + master.getNodeName() + " and " +
                         ri.repEnv.getNodeName() + " are both masters.");
                }
                master = ri.repEnv;
            } else {
                if (doCheckpoint) {
                    RepImpl repImpl = RepInternal.getRepImpl(ri.repEnv);
                    ri.repEnv.close();
                    if (!repImpl.isClosed()) {
                        throw new IllegalStateException
                            ("Environment: " + ri.getEnvHome() +
                             " not released");
                    }
                } else {
                    RepInternal.getRepImpl(ri.repEnv).close(false);
                }
            }
        }

        if (master != null) {
            if (doCheckpoint) {
                master.close();
            } else {
                RepInternal.getRepImpl(master).close(false);
            }
        }
    }

    /**
     * All the non-closed, non-null environments in the array join the group.
     * @return the replicator who is the master.
     */
    public static ReplicatedEnvironment
        openRepEnvsJoin(RepEnvInfo[] repEnvInfo) {

        return joinGroup(getOpenRepEnvs(repEnvInfo));
    }

    /* Get open replicated environments from an array. */
    public static RepEnvInfo[] getOpenRepEnvs(RepEnvInfo[] repEnvInfo) {
        Set<RepEnvInfo> repSet = new HashSet<RepEnvInfo>();
        for (RepEnvInfo ri : repEnvInfo) {
            if ((ri != null) &&
                (ri.getEnv() != null) &&
                !RepInternal.isClosed(ri.getEnv())) {
                repSet.add(ri);
            }
        }

        return repSet.toArray(new RepEnvInfo[repSet.size()]);
    }

    /**
     * Environment handles are created using the config information in
     * repEnvInfo. Note that since this method causes handles to be created
     * serially, it cannot be used to restart an existing group from scratch.
     * It can only be used to start a new group, or have nodes join a group
     * that is already active.
     *
     * @return the replicated environment associated with the master.
     */
    public static
        ReplicatedEnvironment joinGroup(RepEnvInfo ... repEnvInfo) {

        int retries = 10;
        final int retryWaitMillis = 5000;
        ReplicatedEnvironment master = null;
        List<RepEnvInfo> joinNotFinished =
            new LinkedList<RepEnvInfo>(Arrays.asList(repEnvInfo));

        while (joinNotFinished.size() != 0) {
            for (RepEnvInfo ri : joinNotFinished) {
                try {
                    ReplicatedEnvironment.State joinState;
                    if (ri.getEnv() != null) {

                        /*
                         * Handle exists, make sure it's not in UNKNOWN state.
                         */
                        RepImpl rimpl = RepInternal.getRepImpl(ri.getEnv());
                        joinState = rimpl.getState();
                        assert !joinState.equals(State.DETACHED);
                    } else {
                        joinState = ri.openEnv().getState();
                    }

                    if (joinState.equals(State.MASTER)) {
                        if (master != null) {
                            if (--retries > 0) {
                                Thread.sleep(retryWaitMillis);

                                /*
                                 * Start over. The master is slow making its
                                 * transition, one of them has not realized
                                 * that they are no longer the master.
                                 */
                                joinNotFinished = new LinkedList<RepEnvInfo>
                                    (Arrays.asList(repEnvInfo));
                                master = null;
                                break;
                            }
                            throw new RuntimeException
                                ("Dual masters: " + ri.getEnv().getNodeName() +
                                 " and " +
                                 master.getNodeName() + " despite retries");
                        }
                        master = ri.getEnv();
                    }
                    joinNotFinished.remove(ri);
                    if ((joinNotFinished.size() == 0) && (master == null)) {
                        if (--retries == 0) {
                            throw new RuntimeException
                            ("No master established despite retries");
                        }
                        Thread.sleep(retryWaitMillis);
                        /* Start over, an election is still in progress. */
                        joinNotFinished = new LinkedList<RepEnvInfo>
                        (Arrays.asList(repEnvInfo));
                    }
                    break;
                } catch (UnknownMasterException retry) {
                    /* Just retry. */
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return master;
    }

    /**
     * Used to ensure that the entire group is in sync, that is, all replicas
     * are consistent with the master's last commit. Note that it requires all
     * the nodes in the replication group to be available.
     *
     * @param repEnvInfo the array holding the environments
     * @param numSyncNodes the expected number of nodes to be synced; includes
     * the master
     * @throws InterruptedException
     */
    public static VLSN syncGroupToLastCommit(RepEnvInfo[] repEnvInfo,
                                             int numSyncNodes)
        throws InterruptedException {

        CommitToken masterCommitToken = null;

        /*
         * Create a transaction just to make sure all the replicas are awake
         * and connected.
         */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            if (rep.getState().isMaster()) {
                try {
                    Transaction txn=
                        rep.
                        beginTransaction(null, RepTestUtils.SYNC_SYNC_ALL_TC);
                    txn.commit();
                } catch (InsufficientReplicasException e) {
                    if (e.getAvailableReplicas().size() != (numSyncNodes-1)) {
                        throw new IllegalStateException
                            ("Expected replicas: " + (numSyncNodes - 1) +
                             "available replicas: " +
                             e.getAvailableReplicas());
                    }
                }

                /*
                 * Handshakes with all replicas are now completed, if they were
                 * not before. Now get a token to represent the last committed
                 * point in the replication stream, from the master's
                 * perspective.
                 */
                RepNode repNode = RepInternal.getRepImpl(rep).getRepNode();
                masterCommitToken = new CommitToken
                    (repNode.getUUID(),
                     repNode.getCurrentCommitVLSN().getSequence());
                break;
            }
        }

        if (masterCommitToken  == null) {
            throw new IllegalStateException("No current master");
        }

        CommitPointConsistencyPolicy policy =
            new CommitPointConsistencyPolicy(masterCommitToken, MINUTE_MS,
                                             TimeUnit.MILLISECONDS);

        /*
         * Check that the environments are caught up with the last master
         * commit at the time of the call to this method.
         */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            if ((rep == null) ||
                RepInternal.isClosed(rep) ||
                rep.getState().isMaster() ||
                rep.getState().isDetached()) {
                continue;
            }
            policy.ensureConsistency(RepInternal.getRepImpl(rep));
        }
        return new VLSN(masterCommitToken.getVLSN());
    }

    /**
     * Used to ensure that the group is in sync with respect to a given
     * VLSN. If numSyncNodes == repEnvInfo.length, all the nodes in the
     * replication group must be alive and available. If numSyncNodes is less
     * than the size of the group, a quorum will need to be alive and
     * available.
     *
     * @param repEnvInfo the array holding the environments
     * @param numSyncNodes the expected number of nodes to be synced; includes
     * the master
     * @throws InterruptedException
     */
    public static void syncGroupToVLSN(RepEnvInfo[] repEnvInfo,
                                       int numSyncNodes,
                                       VLSN targetVLSN)
        throws InterruptedException {

        /*
         * Create a transaction just to make sure all the replicas are awake
         * and connected.
         */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            if (rep == null) {
                continue;
            }

            if (rep.getState().isMaster()) {
                TransactionConfig txnConfig = null;
                if (numSyncNodes == repEnvInfo.length) {
                    txnConfig = RepTestUtils.SYNC_SYNC_ALL_TC;
                } else {
                    txnConfig = new TransactionConfig();
                    txnConfig.setDurability
                        (new Durability(SyncPolicy.SYNC,
                                        SyncPolicy.SYNC,
                                        ReplicaAckPolicy.SIMPLE_MAJORITY));
                }

                try {
                    Transaction txn = rep.beginTransaction(null, txnConfig);
                    txn.commit();
                } catch (InsufficientReplicasException e) {
                    if (e.getAvailableReplicas().size() !=
                        (numSyncNodes - 1)) {
                        throw new IllegalStateException
                            ("Expected replicas: " + (numSyncNodes - 1) +
                             ", available replicas: " +
                             e.getAvailableReplicas());
                    }
                }
            }
        }

        PointConsistencyPolicy policy = new PointConsistencyPolicy(targetVLSN);

        /* Check that the environments are caught up with this VLSN. */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            if (rep == null ||
                RepInternal.isClosed(rep) ||
                rep.getState().isMaster()) {
                continue;
            }
            policy.ensureConsistency(RepInternal.getRepImpl(rep));
        }
    }

    /**
     * Run utilization profile checking on all databases in the set of
     * RepEnvInfo. The environment must be quiescent. The utility will lock
     * out any cleaning by using DbBackup, during the check.
     */
    public static void checkUtilizationProfile(RepEnvInfo ... repEnvInfo) {
        for (RepEnvInfo info : repEnvInfo) {
            Environment env = info.getEnv();

            /* Use DbBackup to prevent log file deletion. */
            DbBackup backup = new DbBackup(env);
            backup.startBackup();

            try {
                List<String> dbNames = env.getDatabaseNames();

                for (String dbName : dbNames) {
                    DatabaseConfig dbConfig = new DatabaseConfig();
                    DbInternal.setUseExistingConfig(dbConfig, true);
                    dbConfig.setTransactional(true);
                    Database db = env.openDatabase(null, dbName, dbConfig);

                    try {
                        VerifyUtils.checkLsns(db);
                    } finally {
                        db.close();
                    }
                }
            } finally {
                backup.endBackup();
            }
        }
    }

    /**
     * Confirm that all the nodes in this group match. Check number of
     * databases, names of databases, per-database count, per-database
     * records. Use the master node as the reference if it exists, else use the
     * first replicator.
     *
     * @param limit The replication stream portion of the equality check is
     * bounded at the upper end by this value. Limit is usually the commit sync
     * or vlsn sync point explicitly called by a test before calling
     * checkNodeEquality.
     *
     * @throws InterruptedException
     *
     * @throws RuntimeException if there is an incompatibility
     */
    public static void checkNodeEquality(VLSN limit,
                                         boolean verbose,
                                         RepEnvInfo ... repEnvInfo)
        throws InterruptedException {

        int referenceIndex = -1;
        assert repEnvInfo.length > 0;
        for (int i = 0; i < repEnvInfo.length; i++) {
            if ((repEnvInfo[i] == null) ||
                (repEnvInfo[i].getEnv() == null)) {
                continue;
            }
            ReplicatedEnvironment repEnv = repEnvInfo[i].getEnv();
            if (!RepInternal.isClosed(repEnv) && repEnv.getState().isMaster()) {
                referenceIndex = i;
                break;
            }
        }
        assert referenceIndex != -1;

        ReplicatedEnvironment reference = repEnvInfo[referenceIndex].getEnv();
        for (int i = 0; i < repEnvInfo.length; i++) {
            if (i != referenceIndex) {
                if ((repEnvInfo[i] == null) ||
                    (repEnvInfo[i].getEnv() == null)) {
                    continue;
                }

                ReplicatedEnvironment repEnv = repEnvInfo[i].getEnv();
                if (verbose) {
                    System.out.println("Comparing master node " +
                                       reference.getNodeName() +
                                       " to node " +
                                       repEnv.getNodeName());
                }

                if (!RepInternal.isClosed(repEnv)) {
                    checkNodeEquality(reference, repEnv, limit, verbose);
                }
            }
        }
    }

    /* Enable or disable the log cleaning on a replica. */
    private static void runOrPauseCleaners(ReplicatedEnvironment repEnv,
                                           boolean isPaused)
        throws InterruptedException {

        if (!RepInternal.isClosed(repEnv)) {
            RepImpl repImpl = RepInternal.getRepImpl(repEnv);
            if (isPaused) {
                repImpl.getCleaner().addProtectedFileRange(0L);
            } else {
                repImpl.getCleaner().removeProtectedFileRange(0L);
            }
            Thread.sleep(100);
        }
    }

    /**
     * Confirm that the contents of these two nodes match. Check number of
     * databases, names of databases, per-database count, per-database records.
     *
     * @throws InterruptedException
     * @throws RuntimeException if there is an incompatiblity
     */
    public static void checkNodeEquality(ReplicatedEnvironment replicatorA,
                                         ReplicatedEnvironment replicatorB,
                                         VLSN limit,
                                         boolean verbose)
        throws InterruptedException {

        runOrPauseCleaners(replicatorA, true);
        runOrPauseCleaners(replicatorB, true);

        String nodeA = replicatorA.getNodeName();
        String nodeB = replicatorB.getNodeName();

        Environment envA = replicatorA;
        Environment envB = replicatorB;

        RepImpl repImplA = RepInternal.getRepImpl(replicatorA);
        RepImpl repImplB = RepInternal.getRepImpl(replicatorB);

        try {

            /* Compare the replication related sequences. */
            if (verbose) {
                System.out.println("Comparing sequences");
            }

            /* replicated node id sequence. */
            /*
              long nodeIdA =
              envImplA.getNodeSequence().getLastReplicatedNodeId();
              long nodeIdB =
              envImplB.getNodeSequence().getLastReplicatedNodeId();

              // TEMPORARILY DISABLED: sequences not synced up. This may
              // actually apply right now to database and txn ids too,
              // but it's less likely to manifest itself.
              if (nodeIdA != nodeIdB) {
              throw new RuntimeException
              ("NodeId mismatch. " + nodeA +
              " lastRepNodeId=" + nodeIdA + " " + nodeB +
              " lastRepNodeId=" + nodeIdB);
              }
            */

            /* replicated txn id sequence. */
            /*
              long txnIdA = repImplA.getTxnManager().getLastReplicatedTxnId();
              long txnIdB = repmplB.getTxnManager().getLastReplicatedTxnId();
              if (txnIdA != txnIdB) {
              throw new RuntimeException
              ("TxnId mismatch. A.lastRepTxnId=" + txnIdA +
              " B.lastRepTxnId=" + txnIdB);
              }
            */

            /* Replicated database id sequence. */
            int dbIdA = repImplA.getDbTree().getLastReplicatedDbId();
            int dbIdB = repImplB.getDbTree().getLastReplicatedDbId();
            if (dbIdA != dbIdB) {
                throw new RuntimeException
                    ("DbId mismatch. A.lastRepDbId=" + dbIdA +
                     " B.lastRepDbId=" + dbIdB);
            }

            /* Check name and number of application databases first. */
            List<String> dbListA = envA.getDatabaseNames();
            List<String> dbListB = envB.getDatabaseNames();

            if (verbose) {
                System.out.println("envEquals: check db list: " + nodeA +
                                   "=" + dbListA + " " + nodeB + "=" +
                                   dbListB);
            }

            if (!dbListA.equals(dbListB)) {
                throw new RuntimeException("Mismatch: dbNameList " + nodeA +
                                           " =" + dbListA + " " +
                                           nodeB + " =" + dbListB);
            }

            /* Check record count and contents of each database. */
            DatabaseConfig checkConfig = new DatabaseConfig();
            checkConfig.setReadOnly(true);
            checkConfig.setTransactional(true);
            DbInternal.setUseExistingConfig(checkConfig, true);
            for (String dbName : dbListA) {

                Database dbA = null;
                Database dbB = null;
                try {
                    dbA = envA.openDatabase(null, dbName, checkConfig);
                    dbB = envB.openDatabase(null, dbName, checkConfig);

                    long countA = dbA.count();
                    long countB = dbB.count();

                    if (countA != countB) {
                        throw new RuntimeException("Mismatch: db.count() for "
                                                   + dbName + " " + nodeA +
                                                   "=" + countA + " " + nodeB +
                                                   "=" + countB);
                    }
                    checkDbContents(dbA, dbB);

                    if (verbose) {
                        System.out.println("compared " + countA + " records");
                    }
                } finally {
                    if (dbA != null) {
                        dbA.close();
                    }
                    if (dbB != null) {
                        dbB.close();
                    }
                }
            }

            /*
             * Check the replication stream of each environment. The subset of
             * VLSN entries common to both nodes should match.
             */
            checkStreamIntersection(nodeA,
                                    nodeB,
                                    RepInternal.getRepImpl(replicatorA),
                                    RepInternal.getRepImpl(replicatorB),
                                    limit,
                                    verbose);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        runOrPauseCleaners(replicatorA, false);
        runOrPauseCleaners(replicatorB, false);
    }

    /**
     * @throws RuntimeException if dbA and dbB don't have the same contents.
     */
    private static void checkDbContents(Database dbA, Database dbB) {

        Cursor cursorA = null;
        Cursor cursorB = null;
        Transaction txnA = null;
        Transaction txnB = null;
        int debugCount = 0;
        boolean isGroupDB =
            dbA.getDatabaseName().equals(DbTree.REP_GROUP_DB_NAME);

        try {
            txnA = dbA.getEnvironment().beginTransaction(null, null);
            txnB = dbB.getEnvironment().beginTransaction(null, null);
            cursorA = dbA.openCursor(txnA, CursorConfig.READ_UNCOMMITTED);
            cursorB = dbB.openCursor(txnB, CursorConfig.READ_UNCOMMITTED);
            DatabaseEntry keyA = new DatabaseEntry();
            DatabaseEntry keyB = new DatabaseEntry();
            DatabaseEntry dataA = new DatabaseEntry();
            DatabaseEntry dataB = new DatabaseEntry();
            while (cursorA.getNext(keyA, dataA, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
                debugCount++;
                OperationStatus statusB = cursorB.getNext(keyB, dataB,
                                                          LockMode.DEFAULT);
                if (statusB != OperationStatus.SUCCESS) {
                    throw new RuntimeException("Mismatch: debugCount=" +
                                               debugCount + "bad statusB = " +
                                               statusB);
                }
                if (!Arrays.equals(keyA.getData(), keyB.getData())) {
                    throw new RuntimeException("Mismatch: debugCount=" +
                                               debugCount + " keyA=" +
                                               keyA.getData() + " keyB=" +
                                               keyB.getData());

                }
                if (!Arrays.equals(dataA.getData(), dataB.getData())) {
                    if (isGroupDB &&
                        equalsNode(dataA.getData(), dataB.getData())) {
                        continue;
                    }
                    throw new RuntimeException("Mismatch: debugCount=" +
                                               debugCount + " dataA=" +
                                               dataA.getData() + " dataB=" +
                                               dataB.getData());
                }
            }
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (cursorA != null) {
                    cursorA.close();
                }
                if (cursorB != null) {
                    cursorB.close();
                }
                if (txnA != null) {
                    txnA.commit();
                }
                if (txnB != null) {
                    txnB.commit();
                }
            } catch (DatabaseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /*
     * Implements a special check for group nodes which skips the syncup field.
     */
    private static boolean equalsNode(byte[] data1, byte[] data2) {
        NodeBinding nodeBinding = new RepGroupDB.NodeBinding();
        RepNodeImpl n1 = nodeBinding.entryToObject(new TupleInput(data1));
        RepNodeImpl n2 = nodeBinding.entryToObject(new TupleInput(data2));
        return n1.equivalent(n2);
    }

    /**
     * @throws InterruptedException
     * @throws IOException
     * @throws RuntimeException if envA and envB don't have the same set of
     * VLSN mappings, VLSN-tagged log entries, and replication sequences.
     */
    @SuppressWarnings("unused")
    private static void checkStreamIntersection(String nodeA,
                                                String nodeB,
                                                RepImpl repA,
                                                RepImpl repB,
                                                VLSN limit,
                                                boolean verbose)
        throws IOException, InterruptedException {

        if (verbose) {
            System.out.println("Check intersection for " + nodeA +
                               " and " + nodeB);
        }

        VLSNIndex repAMap = repA.getVLSNIndex();
        VLSNRange repARange = repAMap.getRange();
        VLSNIndex repBMap = repB.getVLSNIndex();
        VLSNRange repBRange = repBMap.getRange();

        /*
         * Compare the vlsn ranges held on each environment and find the subset
         * common to both replicas.
         */
        VLSN firstA = repARange.getFirst();
        VLSN lastA = repARange.getLast();
        VLSN firstB = repBRange.getFirst();
        VLSN lastB = repBRange.getLast();
        VLSN lastSyncA = repARange.getLastSync();

        if (lastA.compareTo(limit) < 0) {
            throw new RuntimeException
                ("CheckRepStream error: repA (" + repA.getNameIdPair() +
                 ") lastVLSN = " + lastA +
                 " < limit = " + limit);
        }

        if (lastB.compareTo(limit) < 0) {
            throw new RuntimeException
                ("CheckRepStream error: repB (" + repB.getNameIdPair() +
                 ") lastVLSN = " + lastB +
                 " < limit = " + limit + ")");
        }

        /*
         * Calculate the largest VLSN range starting point and the smallest
         * VLSN range ending point for these two Replicators.
         */
        VLSN firstLarger = (firstA.compareTo(firstB) > 0) ? firstA : firstB;
        VLSN lastSmaller = (lastA.compareTo(lastB) < 0) ? lastA : lastB;

        try {
            /* The two replicas can read from the larger of the first VLSNs. */
            FeederReader readerA = new FeederReader(repA,
                                                    repAMap,
                                                    DbLsn.NULL_LSN,
                                                    100000,
                                                    repA.getNameIdPair());
            readerA.initScan(firstLarger);

            FeederReader readerB = new FeederReader(repB,
                                                    repBMap,
                                                    DbLsn.NULL_LSN,
                                                    100000,
                                                    repB.getNameIdPair());
            readerB.initScan(firstLarger);

            /* They should both find the smaller of the last VLSNs. */
            for (long vlsnVal = firstLarger.getSequence();
                 vlsnVal <= lastSmaller.getSequence();
                 vlsnVal++) {

                OutputWireRecord wireRecordA =
                    readerA.scanForwards(new VLSN(vlsnVal), 0);
                OutputWireRecord wireRecordB =
                    readerB.scanForwards(new VLSN(vlsnVal), 0);

                if (!(wireRecordA.match(wireRecordB))) {
                    throw new RuntimeException(nodeA + " at vlsn " + vlsnVal +
                                               " has " + wireRecordA + " " +
                                               nodeB  + " has " + wireRecordB);
                }

                /* Check that db id, node id, txn id are negative. */
                if (!repA.isConverted()) {
                    wireRecordA.verifyNegativeSequences(nodeA);
                }
                if (!repB.isConverted()) {
                    wireRecordB.verifyNegativeSequences(nodeB);
                }
            }

            if (verbose) {
                System.out.println("Checked from vlsn " + firstLarger +
                                   " to " + lastSmaller);
            }
        } catch (Exception e) {
            e.printStackTrace();

            System.err.println(nodeA + " vlsnMap=");
            repAMap.dumpDb(true);
            System.err.println(nodeB + " vlsnMap=");
            repBMap.dumpDb(true);

            throw new RuntimeException(e);
        }
    }

    /**
     * Return the number of nodes that constitute a quorum for this size
     * group. This should be replaced by ReplicaAckPolicy.requiredNodes;
     */
    public static int getQuorumSize(int groupSize) {
        assert groupSize > 0 : "groupSize = " + groupSize;
        if (groupSize == 1) {
            return 1;
        } else if (groupSize == 2) {
            return 1;
        } else {
            return (groupSize/2) + 1;
        }
    }

    /**
     * Create a rep group of a specified size on the local host, using the
     * default port configuration.
     *
     * @param electableNodes of the electable nodes in the test group
     * @param monitorNodes of the learner nodes in the test group
     *
     * @return the simulated test RepGroup
     *
     * @throws UnknownHostException
     */
    public static RepGroupImpl createTestRepGroup(int electableNodes,
                                                  int monitorNodes)
        throws UnknownHostException {

        Map<Integer, RepNodeImpl> allNodeInfo =
            new HashMap<Integer, RepNodeImpl>();
        final InetAddress ia = InetAddress.getLocalHost();
        int port = getDefaultPort();
        RepGroupImpl repGroup = new RepGroupImpl("TestGroup");

        for (int i=1; i <= electableNodes; i++) {
            allNodeInfo.put(i, new RepNodeImpl(new NameIdPair("node"+i,i),
                                               NodeType.ELECTABLE,
                                               true,
                                               false,
                                               ia.getHostName(),
                                               port,
                                               repGroup.getChangeVersion()));
            port++;
        }
        for (int i= (electableNodes+1);
             i <= (electableNodes+monitorNodes);
             i++) {
            allNodeInfo.put(i, new RepNodeImpl(new NameIdPair("mon"+i,i),
                                               NodeType.MONITOR,
                                               true,
                                               false,
                                               ia.getHostName(),
                                               port,
                                               repGroup.getChangeVersion()));
            port++;
        }
        repGroup.setNodes(allNodeInfo);
        return repGroup;
    }

    public static class RepEnvInfo {
        private final File envHome;
        private final ReplicationConfig repConfig;
        private final EnvironmentConfig envConfig;

        private ReplicatedEnvironment repEnv = null;

        public RepEnvInfo(File envHome,
                          ReplicationConfig repConfig,
                          EnvironmentConfig envConfig) {
            super();
            this.envHome = envHome;
            this.repConfig = repConfig;
            this.envConfig = envConfig;
        }

        public ReplicatedEnvironment openEnv() {

            if (repEnv != null) {
                throw new IllegalStateException("rep env already exists");
            }

            repEnv = new ReplicatedEnvironment(envHome,
                                               getRepConfig(),
                                               envConfig);
            return repEnv;
        }

        public ReplicatedEnvironment openEnv(ReplicaConsistencyPolicy cp) {

            if (repEnv != null) {
                throw new IllegalStateException("rep env already exists");
            }
            repEnv = new ReplicatedEnvironment
                (envHome, getRepConfig(), envConfig, cp,
                 QuorumPolicy.SIMPLE_MAJORITY);
            return repEnv;
        }

        public ReplicatedEnvironment openEnv(RepEnvInfo helper) {

            repConfig.setHelperHosts((helper == null) ?
                                     repConfig.getNodeHostPort() :
                                     helper.getRepConfig().getNodeHostPort());
            return openEnv();
        }

        public ReplicatedEnvironment getEnv() {
            return repEnv;
        }

        public RepImpl getRepImpl() {
            return RepInternal.getRepImpl(repEnv);
        }

        public RepNode getRepNode() {
            return getRepImpl().getRepNode();
        }

        public ReplicationConfig getRepConfig() {
            return repConfig;
        }

        public File getEnvHome() {
            return envHome;
        }

        public EnvironmentConfig getEnvConfig() {
            return envConfig;
        }

        public void closeEnv()  {
            repEnv.close();
            repEnv = null;
        }

        /**
         * Convenience method that guards against a NPE when iterating over
         * a set of RepEnvInfo, looking for the master.
         */
        public boolean isMaster() {
            return (repEnv != null) && repEnv.getState().isMaster();
        }

        /**
         * Convenience method that guards against a NPE when iterating over
         * a set of RepEnvInfo, looking for a replica
         */
        public boolean isReplica() {
            return (repEnv != null) && repEnv.getState().isReplica();
        }

        /**
         * Simulate a crash of the environment, don't do a graceful close.
         */
        public void abnormalCloseEnv() {
            try {
                RepInternal.getRepImpl(repEnv).abnormalClose();
            } catch (DatabaseException ignore) {

                /*
                 * The close will face problems like unclosed txns, ignore.
                 * We're trying to simulate a crash.
                 */
            } finally {
                repEnv = null;
            }
        }

        public String toString() {
            if (repEnv == null) {
                return envHome.toString();
            } else {
                return repEnv.getNodeName();
            }
        }
    }

    public static String stackTraceString(final Throwable exception) {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(bao);
        exception.printStackTrace(printStream);
        String stackTraceString = bao.toString();
        return stackTraceString;
    }

    /**
     * Restarts a group associated with an existing environment on disk.
     * Returns the environment associated with the master.
     */
    public static ReplicatedEnvironment
        restartGroup(RepEnvInfo ... repEnvInfo) {

        return restartGroup(false /*replicasOnly*/, repEnvInfo);
    }

    /**
     * Restarts a group of replicas associated with an existing environment on
     * disk.
     */
    public static void restartReplicas(RepEnvInfo ... repEnvInfo) {

        restartGroup(true /*replicasOnly*/, repEnvInfo);
    }

    /**
     * Restarts a group associated with an existing environment on disk.
     * Returns the environment associated with the master.
     */
    private static ReplicatedEnvironment
        restartGroup(boolean replicasOnly, RepEnvInfo ... repEnvInfo) {

        /* Restart the group, a thread for each node. */
        JoinThread threads[] = new JoinThread[repEnvInfo.length];
        for (int i=0; i < repEnvInfo.length; i++) {
            threads[i]= new JoinThread(repEnvInfo[i]);
            threads[i].start();
        }

        /*
         * Wait for each thread to have joined the group. The group must be
         * re-started in parallel to ensure that all nodes are up and elections
         * can be held.
         */
        for (int i=0; i < repEnvInfo.length; i++) {
            JoinThread jt = threads[i];
            try {
                jt.join(JOIN_WAIT_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (jt.isAlive()) {
                throw new IllegalStateException("Expect JoinThread " + i +
                                                " dead, but it's alive.");
            } 
            final Throwable exception = jt.testException;
            if (exception != null) {
                throw new RuntimeException
                    ("Join thread exception for " + repEnvInfo[i] + "\n" +
                     RepTestUtils.stackTraceString(exception));
            }
        }

        /* All join threads are quiescent, now pick the master. */
        if (replicasOnly) {
            return null;
        }

        ReplicatedEnvironment master = null;
        for (RepEnvInfo ri : repEnvInfo) {
            if (ReplicatedEnvironment.State.MASTER.equals
                    (ri.getEnv().getState())) {
                if (master != null) {
                    throw new IllegalStateException
                        ("Elections are not quiescent.");
                }
                master = ri.getEnv();
            }
        }
        if (master == null) {
            throw new RuntimeException("Can't elect out a master.");
        }
    
        return master;
    }

    /**
     * Threads used to simulate a parallel join group when multiple replication
     * nodes are first brought up for an existing environment.
     */
    private static class JoinThread extends Thread {

        final RepEnvInfo repEnvInfo;

        /* Captures any exception encountered in the process of joining. */
        Throwable testException = null;

        /* The state of the node at the time of joining the group. */
        @SuppressWarnings("unused")
        ReplicatedEnvironment.State state =
            ReplicatedEnvironment.State.UNKNOWN;

        JoinThread(RepEnvInfo repEnvInfo) {
            this.repEnvInfo = repEnvInfo;
        }

        @Override
        public void run() {
            try {
                state = repEnvInfo.openEnv().getState();
            } catch (Throwable e) {
                testException = e;
            }
        }
    }

    /**
     * Issue DbSync on a group. All nodes are presumed to be closed.
     */
    public static void syncupGroup(RepEnvInfo ... repEnvInfo) {

        /*
         * The call to DbSync blocks until the sync is done, so it must
         * be executed concurrently by a set of threads.
         */
        SyncThread threads[] = new SyncThread[repEnvInfo.length];
        String helperHost = repEnvInfo[0].getRepConfig().getNodeHostPort();
        for (int i=0; i < repEnvInfo.length; i++) {
            threads[i]= new SyncThread(repEnvInfo[i], helperHost);
            threads[i].start();
        }

        /*
         * Wait for each thread to open, sync, and close the node.
         */
        for (int i=0; i < repEnvInfo.length; i++) {
            SyncThread t = threads[i];
            try {
                t.join(JOIN_WAIT_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (t.isAlive()) {
                throw new IllegalStateException("Expect SyncThread " + i + 
                                                " dead, but it's alive.");
            }
            final Throwable exception = t.testException;
            if (exception != null) {
                throw new RuntimeException
                    ("Join thread exception.\n" +
                     RepTestUtils.stackTraceString(exception));
            }
        }
    }

    /**
     * Threads used to simulate a parallel join group when multiple replication
     * nodes are first brought up for an existing environment.
     */
    private static class SyncThread extends Thread {

        final RepEnvInfo repEnvInfo;
        final String helperHost;

        /* Captures any exception encountered in the process of joining. */
        Throwable testException = null;

        SyncThread(RepEnvInfo repEnvInfo, String helperHost) {
            this.repEnvInfo = repEnvInfo;
            this.helperHost = helperHost;
        }

        @Override
        public void run() {
            try {
                ReplicationConfig config = repEnvInfo.getRepConfig();
                DbSync syncAgent =
                    new DbSync(repEnvInfo.getEnvHome().toString(),
                               config.getGroupName(),
                               config.getNodeName(),
                               config.getNodeHostPort(),
                               helperHost,
                               JOIN_WAIT_TIME);
                syncAgent.sync();
            } catch (Throwable e) {
                testException = e;
            }
        }
    }

}
