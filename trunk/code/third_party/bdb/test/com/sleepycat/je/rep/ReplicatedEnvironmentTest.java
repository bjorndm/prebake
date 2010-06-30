/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReplicatedEnvironmentTest.java,v 1.33 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.stream.ReplicaFeederSyncup;
import com.sleepycat.je.rep.stream.ReplicaFeederSyncup.TestHook;
import com.sleepycat.je.rep.utilint.RepUtils;

/*
 * TODO:
 * 1) Test null argument for repConfig
 *
 */
/**
 * Check ReplicatedEnvironment-specific functionality; environment-specific
 * functionality is covered via the DualTest infrastructure.
 */
public class ReplicatedEnvironmentTest extends RepTestBase {

    /**
     * Test to validate the code fragments contained in the javdoc for the
     * ReplicatedEnvironment class, or to illustrate statements made there.
     */
    public void testClassJavadoc()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig();
        ReplicationConfig repEnvConfig = getRepEnvConfig();

        ReplicatedEnvironment repEnv =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

        repEnv.close();

        repEnv = new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
        RepImpl repImpl = repEnv.getRepImpl();
        assertTrue(repImpl != null);

        repEnv.close();
        /* It's ok to check after it's closed. */
        try {
            repEnv.getState();
            fail("expected exception");
        } catch (IllegalStateException e) {
            /* Expected. */
        }
    }

    /**
     * This is literally the snippet of code used as an 
     * startup example. Test here to make sure it compiles.
     */
    public void testExample() {
        File envHome = new File(".");
        try { 
            /******* begin example *************/
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);

            // Identify the node
            ReplicationConfig repConfig = new ReplicationConfig();
            repConfig.setGroupName("PlanetaryRepGroup");
            repConfig.setNodeName("mercury");
            repConfig.setNodeHostPort("mercury.acme.com:5001");

            // This is the first node, so its helper is itself
            repConfig.setHelperHosts("mercury.acme.com:5001");
 
            ReplicatedEnvironment repEnv =
                new ReplicatedEnvironment(envHome, repConfig, envConfig);
            /******* end example *************/
        } catch (IllegalArgumentException expected) {
        }

        try {
            /******* begin example *************/
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);
 
            // Identify the node
            ReplicationConfig repConfig = 
                new ReplicationConfig("PlanetaryRepGroup", "Jupiter",
                                      "jupiter.acme.com:5002");
 
            // Use the node at mercury.acme.com:5001 as a helper to find the
            // rest of the group.
            repConfig.setHelperHosts("mercury.acme.com:5001");
 
            ReplicatedEnvironment repEnv =
                new ReplicatedEnvironment(envHome, repConfig, envConfig);

            /******* end example *************/
        } catch (IllegalArgumentException expected) {
        }
    }

    private EnvironmentConfig getEnvConfig() {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        return envConfig;
    }

    private ReplicationConfig getRepEnvConfig() {
        /* ** DO NOT ** use localhost in javadoc. */
        ReplicationConfig repEnvConfig = 
            new ReplicationConfig("ExampleGroup", "node1", "localhost:5000");

        /* Configure it to be the master. */
        repEnvConfig.setHelperHosts(repEnvConfig.getNodeHostPort());
        return repEnvConfig;
    }

    public void testJoinGroupJavadoc()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig();
        ReplicationConfig repEnvConfig = getRepEnvConfig();

        ReplicatedEnvironment repEnv1 =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

        assertEquals(ReplicatedEnvironment.State.MASTER, repEnv1.getState());

        ReplicatedEnvironment repEnv2 =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
        assertEquals(ReplicatedEnvironment.State.MASTER, repEnv2.getState());

        repEnv1.close();
        repEnv2.close();
    }

    /**
     * Verify exceptions resulting from timeouts due to slow syncups, or
     * because the Replica was too far behind and could not catch up in the
     * requisite time.
     */
    public void testRepEnvTimeout()
        throws DatabaseException {

        createGroup();
        repEnvInfo[2].closeEnv();
        populateDB(repEnvInfo[0].getEnv(), "db", 10);

        /* Get past syncup for replica consistency exception. */
        repEnvInfo[2].getRepConfig().setConfigParam
            (RepParams.ENV_SETUP_TIMEOUT.getName(), "1000 ms");

        TestHook<Object> syncupEndHook = new TestHook<Object>() {
            public void doHook() throws InterruptedException {
                Thread.sleep(Integer.MAX_VALUE);
            }
        };

        ReplicaFeederSyncup.setGlobalSyncupEndHook(syncupEndHook);

        /* Syncup driven exception. */
        try {
            repEnvInfo[2].openEnv();
        } catch (ReplicaConsistencyException ume) {
            /* Expected exception. */
        }
        ReplicaFeederSyncup.setGlobalSyncupEndHook(null);

        repEnvInfo[2].getRepConfig().setConfigParam
            (RepParams.TEST_REPLICA_DELAY.getName(),
             Integer.toString(Integer.MAX_VALUE));
        repEnvInfo[2].getRepConfig().setConfigParam
            (ReplicationConfig.ENV_CONSISTENCY_TIMEOUT, "1000 ms");
        try {
            repEnvInfo[2].openEnv();
        } catch (ReplicaConsistencyException ume) {
            /* Expected exception. */
        }
    }

    /*
     * Ensure that default consistency policy can be overridden in the handle.
     */
    public void testRepEnvConfig()
        throws DatabaseException {

        EnvironmentConfig envConfig = getEnvConfig();

        ReplicationConfig repEnvConfig = getRepEnvConfig();

        ReplicatedEnvironment repEnv1 =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

        /* Verify that default is used. */
        ReplicationConfig repConfig1 = repEnv1.getRepConfig();
        assertEquals(RepUtils.getReplicaConsistencyPolicy
                     (RepParams.CONSISTENCY_POLICY.getDefault()),
                     repConfig1.getConsistencyPolicy());

        /* Override the policy in the handle. */
        repEnvConfig.setConsistencyPolicy
            (NoConsistencyRequiredPolicy.NO_CONSISTENCY);

        ReplicatedEnvironment repEnv2 =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
        ReplicationConfig repConfig2 = repEnv2.getRepConfig();
        /* New handle should have new policy. */
        assertEquals(NoConsistencyRequiredPolicy.NO_CONSISTENCY,
                     repConfig2.getConsistencyPolicy());

        /* Old handle should retain the old default policy. */
        assertEquals(RepUtils.getReplicaConsistencyPolicy
                     (RepParams.CONSISTENCY_POLICY.getDefault()),
                     repConfig1.getConsistencyPolicy());

        /* Default should be retained for new handles. */
        repEnvConfig = new ReplicationConfig();
        repEnvConfig.setGroupName("ExampleGroup");
        repEnvConfig.setNodeName("node1");
        repEnvConfig.setNodeHostPort("localhost:5000");
        ReplicatedEnvironment repEnv3 =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
        ReplicationConfig repConfig3 = repEnv3.getRepConfig();
        assertEquals(RepUtils.getReplicaConsistencyPolicy
                     (RepParams.CONSISTENCY_POLICY.getDefault()),
                     repConfig3.getConsistencyPolicy());

        repEnv1.close();
        repEnv2.close();
        repEnv3.close();
    }

    /*
     * Ensure that only a r/o standalone Environment can open on a closed
     * replicated Environment home directory.
     */
    public void testEnvOpenOnRepEnv()
        throws DatabaseException {

        final EnvironmentConfig envConfig = getEnvConfig();
        final ReplicationConfig repEnvConfig = getRepEnvConfig();
        final DatabaseConfig dbConfig = getDbConfig();

        final ReplicatedEnvironment repEnv =
            new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

        Database db = repEnv.openDatabase(null, "db1", dbConfig);
        final DatabaseEntry dk = new DatabaseEntry(new byte[10]);
        final DatabaseEntry dv = new DatabaseEntry(new byte[10]);
        OperationStatus stat = db.put(null, dk, dv);
        assertEquals(OperationStatus.SUCCESS, stat);
        db.close();
        repEnv.close();

        try {
            Environment env = new Environment(envRoot, envConfig);
            env.close();
            fail("expected exception");
        } catch (UnsupportedOperationException e) {
            /* Expected. */
        }
        envConfig.setReadOnly(true);
        dbConfig.setReadOnly(true);

        /* Open the replicated environment as read-only. Should be OK. */
        Environment env = new Environment(envRoot, envConfig);
        Transaction txn = env.beginTransaction(null, null);
        db = env.openDatabase(txn, "db1", dbConfig);
        stat = db.get(txn, dk, dv, LockMode.READ_COMMITTED);
        assertEquals(OperationStatus.SUCCESS, stat);

        db.close();
        txn.commit();
        env.close();
    }

    /*
     * Ensure JE would throw out UnsupportedOperationException if opens a r/w
     * standalone Environment on a opened replicated Environment home
     * directory.
     */
    public void testOpenEnvOnAliveRepEnv()
        throws DatabaseException {

        final EnvironmentConfig envConfig = getEnvConfig();
        final ReplicationConfig repConfig = getRepEnvConfig();

        ReplicatedEnvironment repEnv =
            new ReplicatedEnvironment(envRoot, repConfig, envConfig);

        Environment env = null;
        try {
            env = new Environment(envRoot, envConfig);
            env.close();
            fail("expected exception");
        } catch (UnsupportedOperationException e) {
            /* Expected */
        }

        envConfig.setReadOnly(true);

        try {
            env = new Environment(envRoot, envConfig);
            env.close();
            fail("expected exception");
        } catch (IllegalArgumentException e) {

            /*
             * Expect IllegalArgumentException since the ReplicatedEnvironment
             * this Environment open is not read only.
             */
        }

        repEnv.close();
    }

    public void testRepEnvUsingEnvHandle()
        throws DatabaseException {

        final EnvironmentConfig envConfig = getEnvConfig();
        final DatabaseConfig dbConfig = getDbConfig();
        final DatabaseEntry dk = new DatabaseEntry(new byte[10]);
        final DatabaseEntry dv = new DatabaseEntry(new byte[10]);

        {
            final ReplicationConfig repEnvConfig = getRepEnvConfig();
            final ReplicatedEnvironment repEnv1 =
                new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

            final Database db = repEnv1.openDatabase(null, "db1", dbConfig);

            OperationStatus stat = db.put(null, dk, dv);
            assertEquals(OperationStatus.SUCCESS, stat);
            db.close();
            repEnv1.close();
        }

        envConfig.setReadOnly(true);
        final Environment env = new Environment(envRoot, envConfig);
        dbConfig.setReadOnly(true);

        final Transaction t1 = env.beginTransaction(null, null);
        final Database db = env.openDatabase(null, "db1", dbConfig);

        try {
            /* Read operations ok. */
            OperationStatus stat = db.get(t1, dk, dv, LockMode.DEFAULT);
            assertEquals(OperationStatus.SUCCESS, stat);
        } catch (Exception e) {
            fail("Unexpected exception" + e);
        }
        t1.commit();

        /*
         * Iterate over all update operations that must fail, using auto and
         * explicit commit.
         */
        for (TryOp op : new TryOp[] {
                new TryOp(UnsupportedOperationException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        db.put(t, dk, dv);
                    }
                },

                new TryOp(UnsupportedOperationException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        db.delete(t, dk);
                    }
                },

                new TryOp(IllegalArgumentException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        env.openDatabase(t, "db2", dbConfig);
                    }
                },

                new TryOp(UnsupportedOperationException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        env.truncateDatabase(t, "db1", false);
                    }
                },

                new TryOp(UnsupportedOperationException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        env.renameDatabase(t, "db1", "db2");
                    }
                },

                new TryOp(UnsupportedOperationException.class) {
                    @Override
                    void exec(Transaction t)
                        throws DatabaseException {

                        env.removeDatabase(t, "db1");
                    }
                }}) {
            for (final Transaction t : new Transaction[] {
                    env.beginTransaction(null, null), null}) {
                try {
                    op.exec(t);
                    fail("expected exception");
                } catch (RuntimeException e) {
                    if (!op.expectedException.equals(e.getClass())) {
                        e.printStackTrace();
                        fail("unexpected exception." +
                             "Expected: " + op.expectedException +
                             "Threw: " + e.getClass());
                    }
                    if (t != null) {
                        t.abort();
                        continue;
                    }
                }
                if (t != null)  {
                    t.commit();
                }
            }
        }
        db.close();
        env.close();
    }

    /*
     * Ensure that an exception is thrown when we open a replicated env, put
     * some data in it, close the env, and then open a r/o replicated env.
     */
    public void testReadOnlyRepEnvUsingEnvHandleSR17643()
        throws DatabaseException {

        final EnvironmentConfig envConfig = getEnvConfig();
        final DatabaseConfig dbConfig = getDbConfig();
        final DatabaseEntry dk = new DatabaseEntry(new byte[10]);
        final DatabaseEntry dv = new DatabaseEntry(new byte[10]);

        {
            final ReplicationConfig repEnvConfig = getRepEnvConfig();
            final ReplicatedEnvironment repEnv1 =
                new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);

            final Database db = repEnv1.openDatabase(null, "db1", dbConfig);

            OperationStatus stat = db.put(null, dk, dv);
            assertEquals(OperationStatus.SUCCESS, stat);
            db.close();
            repEnv1.close();
        }

        try {
            final ReplicationConfig repEnvConfig = getRepEnvConfig();
            envConfig.setReadOnly(true);
            final ReplicatedEnvironment repEnv1 =
                new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
            repEnv1.close();
            fail("expected an exception");
        } catch (IllegalArgumentException IAE) {
            /* Expected. */
        }
    }

    private DatabaseConfig getDbConfig() {
        final DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        return dbConfig;
    }

    abstract class TryOp {
        Class<?> expectedException;

        TryOp(Class<?> expectedException) {
            this.expectedException = expectedException;
        }

        abstract void exec(Transaction t) throws DatabaseException;
    }

    /*
     * Verify that requirements on the environment config imposed by
     * replication are enforced.
     */
    public void testEnvConfigRequirements()
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();

        ReplicationConfig repEnvConfig =  
            new ReplicationConfig("ExampleGroup", "node1", "localhost:5000");
        ReplicatedEnvironment repEnv = null;
        try {
            repEnv =
                new ReplicatedEnvironment(envRoot, repEnvConfig, envConfig);
            repEnv.close();
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            /* Expected. */
        }
    }
}
