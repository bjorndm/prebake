/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: PerDbReplicationTest.java,v 1.14 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.util.TestUtils;

/**
 * Make sure the unadvertised per-db replication config setting works.
 */
public class PerDbReplicationTest extends TestCase {

    private static final String TEST_DB = "testdb";
    private final File envRoot;
    private Environment env;
    private Database db;

    public PerDbReplicationTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    public void tearDown() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    /**
     * A database in a replicated environment should replicate by default.
     */
    public void testDefault() {
//        Replicator[] replicators = RepTestUtils.startGroup(envRoot,
//                                                           1,
//                                                           false /* verbose */);
//        try {
//            env = replicators[0].getEnvironment();
//            DatabaseConfig config = new DatabaseConfig();
//            config.setAllowCreate(true);
//            config.setTransactional(true);
//
//            validate(config, true /* replicated */);
//        } finally {
//            if (db != null) {
//                db.close();
//            }
//
//            for (Replicator rep: replicators) {
//                rep.close();
//            }
//        }
    }

    /**
     * Check that a database in a replicated environment which is configured to
     * not replicate is properly saved.
     * (Not a public feature yet).
     */
    public void testNotReplicated() {
//        Replicator[] replicators = RepTestUtils.startGroup(envRoot,
//                                                           1,
//                                                           false /* verbose*/);
//        try {
//            env = replicators[0].getEnvironment();
//            DatabaseConfig config = new DatabaseConfig();
//            config.setAllowCreate(true);
//            config.setTransactional(true);
//            DbInternal.setReplicated(config, false);
//
//            validate(config, false /* replicated */);
//        } finally {
//            if (db != null) {
//                db.close();
//            }
//
//            for (Replicator rep: replicators) {
//                rep.close();
//            }
//        }
    }

    /**
     * A database in a standalone environment should not be replicated.
     */
    public void testStandalone()
        throws DatabaseException {

        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            env = new Environment(envRoot, envConfig);
            DatabaseConfig config = new DatabaseConfig();
            config.setAllowCreate(true);

            validate(config, false /* replicated */);
        } finally {
            if (db != null) {
                db.close();
            }

            if (env != null) {
                env.close();
            }
        }
    }

    /*
     * Check that the notReplicate attribute is properly immutable and
     * persistent.
     */
    private void validate(DatabaseConfig config,
                          boolean replicated)
            throws DatabaseException {

        /* Create the database -- is its config what we expect? */
        db = env.openDatabase(null, TEST_DB, config);
        DatabaseConfig inUseConfig = db.getConfig();
        assertEquals(replicated,
                     DbInternal.getReplicated(inUseConfig));

        /* Close, re-open. */
        db.close();
        db = null;
        db = env.openDatabase(null, TEST_DB, inUseConfig);
        assertEquals(replicated,
                     DbInternal.getReplicated(db.getConfig()));

        /*
         * Close, re-open w/inappropriate value for the replicated bit. This is
         * only checked for replicated environments.
         */
        db.close();
        db = null;
        if (DbInternal.getEnvironmentImpl(env).isReplicated()) {
            DbInternal.setReplicated(inUseConfig, !replicated);
            try {
                db = env.openDatabase(null, TEST_DB, inUseConfig);
                fail("Should have caught config mismatch");
            } catch (IllegalArgumentException expected) {
            }
        }
    }
}
