/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepEnvWrapper.java,v 1.28 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.EnvTestWrapper;
import com.sleepycat.je.utilint.VLSN;

/**
 * An environment wrapper for replicated tests.
 */
public class RepEnvWrapper extends EnvTestWrapper {

    // TODO: a standard way to parameterize all replication tests.
    final int repNodes = 3;

    private boolean doNodeEqualityCheck = true;

    private final Map<File, RepEnvInfo[]> dirRepEnvInfoMap =
        new HashMap<File, RepEnvInfo[]>();

    @Override
    public Environment create(File envRootDir, EnvironmentConfig envConfig)
        throws DatabaseException {

        adjustEnvConfig(envConfig);

        RepEnvInfo[] repEnvInfo = dirRepEnvInfoMap.get(envRootDir);
        Environment env = null;
        if (repEnvInfo != null) {
            /* An existing environment */
            try {
                repEnvInfo = RepTestUtils.setupEnvInfos
                    (envRootDir, repNodes, envConfig);
            } catch (Exception e1) {
                TestCase.fail(e1.getMessage());
            }
            env = restartGroup(repEnvInfo);
        } else {
            /* Eliminate detritus from earlier failed tests. */
            RepTestUtils.removeRepEnvironments(envRootDir);
            try {
                repEnvInfo =
                    RepTestUtils.setupEnvInfos(envRootDir,
                                               repNodes,
                                               envConfig);
                RepTestUtils.joinGroup(repEnvInfo);
                TestCase.assertTrue(repEnvInfo[0].getEnv().
                                    getState().isMaster());
                env = repEnvInfo[0].getEnv();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        dirRepEnvInfoMap.put(envRootDir, repEnvInfo);
        return env;
    }

    /**
     * Modifies the config to suppress the cleaner and replace use of obsolete
     * sync apis with the Durability api to avoid mixed mode exceptions from
     * Environment.checkTxnConfig.
     */
    @SuppressWarnings("deprecation")
    private void adjustEnvConfig(EnvironmentConfig envConfig)
        throws IllegalArgumentException {

        // TODO: Remove when the HA/cleaner integration is ready
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

        /*
         * Replicated tests use multiple environments, configure shared cache
         * to reduce the memory consumption.
         */
        envConfig.setSharedCache(true);

        boolean sync = false;
        boolean writeNoSync = envConfig.getTxnWriteNoSync();
        boolean noSync = envConfig.getTxnNoSync();
        envConfig.setTxnWriteNoSync(false);
        envConfig.setTxnNoSync(false);
        envConfig.setDurability(getDurability(sync, writeNoSync, noSync));
    }

    /**
     * Restarts a group associated with an existing environment on disk.
     * Returns the environment associated with the master.
     */
    private Environment restartGroup(RepEnvInfo[] repEnvInfo) {
        return RepTestUtils.restartGroup(repEnvInfo);

    }

    private void closeInternal(Environment env, boolean doCheckpoint) {

        File envRootDir = env.getHome().getParentFile();

        RepEnvInfo[] repEnvInfo = dirRepEnvInfoMap.get(envRootDir);
        try {
            ReplicatedEnvironment master = null;
            for (RepEnvInfo ri : repEnvInfo) {
                ReplicatedEnvironment r = ri.getEnv();
                if (r == null) {
                    continue;
                }
                if (r.getState() == ReplicatedEnvironment.State.MASTER) {
                    master = r;
                }
            }
            TestCase.assertNotNull(master);
            VLSN lastVLSN = RepInternal.getRepImpl(master).getRepNode().
                getVLSNIndex().getRange().getLast();
            RepTestUtils.syncGroupToVLSN(repEnvInfo, repNodes, lastVLSN);

            if (doNodeEqualityCheck) {
                RepTestUtils.checkNodeEquality(lastVLSN, false, repEnvInfo);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RepTestUtils.shutdownRepEnvs(repEnvInfo, doCheckpoint);
    }

    /* Close environment with a checkpoint. */
    @Override
    public void close(Environment env)
        throws DatabaseException {

        closeInternal(env, true);
    }

    /* Close environment without a checkpoint. */
    @Override
    public void closeNoCheckpoint(Environment env)
        throws DatabaseException {

        closeInternal(env, false);
    }

    @Override
    public void destroy() {
        for (File f : dirRepEnvInfoMap.keySet()) {
            RepTestUtils.removeRepEnvironments(f);
        }
        dirRepEnvInfoMap.clear();
    }

    /**
     * Convert old style sync into Durability as defined in TxnConfig.
     *
     * @see com.sleepycat.je.TransactionConfig.getDurabilityFromSync
     */
    private Durability getDurability(boolean sync,
                                     boolean writeNoSync,
                                     boolean noSync) {
        if (sync) {
            return Durability.COMMIT_SYNC;
        } else if (writeNoSync) {
            return Durability.COMMIT_WRITE_NO_SYNC;
        } else if (noSync) {
            return Durability.COMMIT_NO_SYNC;
        }
        return Durability.COMMIT_SYNC;
    }

    @Override
    public void resetNodeEqualityCheck() {
        doNodeEqualityCheck = !doNodeEqualityCheck;
    }
}
