/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: UtilizationTest.java,v 1.37 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.ChecksumException;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogSource;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Test utilization counting of LNs.
 */
public class UtilizationTest extends TestCase {

    private static final String DB_NAME = "foo";

    private static final String OP_NONE = "op-none";
    private static final String OP_CHECKPOINT = "op-checkpoint";
    private static final String OP_RECOVER = "op-recover";
    //private static final String[] OPERATIONS = { OP_NONE, };
    //*
    private static final String[] OPERATIONS = { OP_NONE,
                                                 OP_CHECKPOINT,
                                                 OP_RECOVER,
                                                 OP_RECOVER };
    //*/

    /*
     * Set fetchObsoleteSize=true only for the second OP_RECOVER test.
     * We check that OP_RECOVER works with without fetching, but with fetching
     * we check that all LN sizes are counted.
     */
    private static final boolean[] FETCH_OBSOLETE_SIZE = { false,
                                                           false,
                                                           false,
                                                           true };

    private static final CheckpointConfig forceConfig = new CheckpointConfig();
    static {
        forceConfig.setForce(true);
    }

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private DatabaseImpl dbImpl;
    private boolean dups = false;
    private DatabaseEntry keyEntry = new DatabaseEntry();
    private DatabaseEntry dataEntry = new DatabaseEntry();
    private String operation;
    private long lastFileSeen;
    private boolean fetchObsoleteSize;
    private boolean truncateOrRemoveDone;

    public static Test suite() {
        TestSuite allTests = new TestSuite();
        for (int i = 0; i < OPERATIONS.length; i += 1) {
            TestSuite suite = new TestSuite(UtilizationTest.class);
            Enumeration e = suite.tests();
            while (e.hasMoreElements()) {
                UtilizationTest test = (UtilizationTest) e.nextElement();
                test.init(OPERATIONS[i], FETCH_OBSOLETE_SIZE[i]);
                allTests.addTest(test);
            }
        }
        return allTests;
    }

    public UtilizationTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    private void init(String operation, boolean fetchObsoleteSize) {
        this.operation = operation;
        this.fetchObsoleteSize = fetchObsoleteSize;
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        /* Set test name for reporting; cannot be done in the ctor or setUp. */
        setName(operation +
                (fetchObsoleteSize ? "-fetch" : "") +
                ':' + getName());

        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        try {
            //*
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
            //*/
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        db = null;
        dbImpl = null;
        env = null;
        envImpl = null;
        envHome = null;
        keyEntry = null;
        dataEntry = null;
    }

    /**
     * Opens the environment and database.
     */
    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig config = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(config);
        config.setTransactional(true);
        config.setTxnNoSync(true);
        config.setAllowCreate(true);
        /* Do not run the daemons. */
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        /* Use a tiny log file size to write one LN per file. */
        config.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                              Integer.toString(64));

        /* Obsolete LN size counting is optional per test. */
        if (fetchObsoleteSize) {
            config.setConfigParam
                (EnvironmentParams.CLEANER_FETCH_OBSOLETE_SIZE.getName(),
                 "true");
        }

        env = new Environment(envHome, config);
        envImpl = DbInternal.getEnvironmentImpl(env);

        /* Speed up test that uses lots of very small files. */
        envImpl.getFileManager().setSyncAtFileEnd(false);

        openDb();
    }

    /**
     * Opens the database.
     */
    private void openDb()
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        db = env.openDatabase(null, DB_NAME, dbConfig);
        dbImpl = DbInternal.getDatabaseImpl(db);
    }

    /**
     * Closes the environment and database.
     */
    private void closeEnv(boolean doCheckpoint)
        throws DatabaseException {

        /*
         * We pass expectAccurateDbUtilization as false when
         * truncateOrRemoveDone, because the database utilization info for that
         * database is now gone.
         */
        VerifyUtils.verifyUtilization
            (envImpl,
             true, // expectAccurateObsoleteLNCount
             expectAccurateObsoleteLNSize(),
             !truncateOrRemoveDone); // expectAccurateDbUtilization

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            envImpl.close(doCheckpoint);
            env = null;
        }
    }

    public void testReuseSlotAfterDelete()
        throws DatabaseException {

        openEnv();

        /* Insert and delete without compress to create a knownDeleted slot. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doDelete(0, txn);
        txn.commit();

        /* Insert key 0 to reuse the knownDeleted slot. */
        txn = env.beginTransaction(null, null);
        long file2 = doPut(0, txn);
        /* Delete and insert to reuse deleted slot in same txn. */
        long file3 = doDelete(0, txn);
        long file4 = doPut(0, txn);
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);
        expectObsolete(file2, true);
        expectObsolete(file3, true);
        expectObsolete(file4, false);

        closeEnv(true);
    }

    public void testReuseKnownDeletedSlot()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and abort to create a knownDeleted slot.  */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        txn.abort();

        /* Insert key 0 to reuse the knownDeleted slot. */
        txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        txn.commit();
        performRecoveryOperation();

        /* Verify that file0 is still obsolete. */
        expectObsolete(file0, true);
        expectObsolete(file1, false);

        closeEnv(true);
    }

    public void testReuseKnownDeletedSlotAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and abort to create a knownDeleted slot.  */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        txn.abort();

        /* Insert key 0 to reuse the knownDeleted slot, and abort. */
        txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        txn.abort();
        performRecoveryOperation();

        /* Verify that file0 is still obsolete. */
        expectObsolete(file0, true);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testReuseKnownDeletedSlotDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert {0, 2} and abort to create a knownDeleted slot. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn); // 3rd LN
        long file4 = file3 + 1;        // DupCountLN
        txn.abort();

        /* Insert {0, 2} to reuse the knownDeleted slot. */
        txn = env.beginTransaction(null, null);
        long file5 = doPut(0, 2, txn); // 4th LN
        long file6 = file5 + 1;        // DupCountLN
        txn.commit();
        performRecoveryOperation();

        /* Verify that file3 is still obsolete. */
        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, false);
        expectObsolete(file6, false);

        closeEnv(true);
    }

    public void testReuseKnownDeletedSlotDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert {0, 2} and abort to create a knownDeleted slot. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn); // 3rd LN
        long file4 = file3 + 1;        // DupCountLN
        txn.abort();

        /* Insert {0, 2} to reuse the knownDeleted slot, then abort. */
        txn = env.beginTransaction(null, null);
        long file5 = doPut(0, 2, txn); // 4th LN
        long file6 = file5 + 1;        // DupCountLN
        txn.abort();
        performRecoveryOperation();

        /* Verify that file3 is still obsolete. */
        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, true);
        expectObsolete(file6, true);

        closeEnv(true);
    }

    public void testInsert()
        throws DatabaseException {

        openEnv();

        /* Insert key 0. */
        long file0 = doPut(0, true);
        performRecoveryOperation();

        /* Expect that LN is not obsolete. */
        FileSummary fileSummary = getFileSummary(file0);
        assertEquals(1, fileSummary.totalLNCount);
        assertEquals(0, fileSummary.obsoleteLNCount);
        DbFileSummary dbFileSummary = getDbFileSummary(file0);
        assertEquals(1, dbFileSummary.totalLNCount);
        assertEquals(0, dbFileSummary.obsoleteLNCount);

        closeEnv(true);
    }

    public void testInsertAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0. */
        long file0 = doPut(0, false);
        performRecoveryOperation();

        /* Expect that LN is obsolete. */
        FileSummary fileSummary = getFileSummary(file0);
        assertEquals(1, fileSummary.totalLNCount);
        assertEquals(1, fileSummary.obsoleteLNCount);
        DbFileSummary dbFileSummary = getDbFileSummary(file0);
        assertEquals(1, dbFileSummary.totalLNCount);
        assertEquals(1, dbFileSummary.obsoleteLNCount);

        closeEnv(true);
    }

    public void testInsertDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert key 0 and a dup. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn);
        long file3 = doPut(0, 1, txn);
        txn.commit();
        performRecoveryOperation();

        /*
         * The dup tree is created on 2nd insert.  In between the two
         * DupCountLNs are two INs.
         */
        long file1 = file0 + 1; // DupCountLN (provisional)
        long file2 = file1 + 3; // DupCountLN (non-provisional)
        assertEquals(file3, file2 + 1); // new LN

        expectObsolete(file0, false); // 1st LN
        expectObsolete(file1, true);  // 1st DupCountLN
        expectObsolete(file2, false); // 2nd DupCountLN
        expectObsolete(file3, false); // 2nd LN

        closeEnv(true);
    }

    public void testInsertDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert key 0 and a dup. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn);
        long file3 = doPut(0, 1, txn);
        txn.abort();
        performRecoveryOperation();

        /*
         * The dup tree is created on 2nd insert.  In between the two
         * DupCountLNs are two INs.
         */
        long file1 = file0 + 1; // DupCountLN (provisional)
        long file2 = file1 + 3; // DupCountLN (non-provisional)
        assertEquals(file3, file2 + 1); // new LN

        expectObsolete(file0, true);  // 1st LN
        expectObsolete(file1, false); // 1st DupCountLN
        expectObsolete(file2, true);  // 2nd DupCountLN
        expectObsolete(file3, true);  // 2nd LN

        closeEnv(true);
    }

    public void testUpdate()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update key 0. */
        long file1 = doPut(0, true);
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, false);

        closeEnv(true);
    }

    public void testUpdateAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update key 0 and abort. */
        long file1 = doPut(0, false);
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testUpdateDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update {0, 0}. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 0, txn); // 3rd LN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, false);

        closeEnv(true);
    }

    public void testUpdateDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update {0, 0}. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 0, txn); // 3rd LN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);

        closeEnv(true);
    }

    public void testDelete()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Delete key 0. */
        long file1 = doDelete(0, true);
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testDeleteAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Delete key 0 and abort. */
        long file1 = doDelete(0, false);
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testDeleteDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Delete {0, 0} and abort. */
        txn = env.beginTransaction(null, null);
        long file3 = doDelete(0, 0, txn); // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, false);

        closeEnv(true);
    }

    public void testDeleteDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Delete {0, 0} and abort. */
        txn = env.beginTransaction(null, null);
        long file3 = doDelete(0, 0, txn); // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);

        closeEnv(true);
    }

    public void testInsertUpdate()
        throws DatabaseException {

        openEnv();

        /* Insert and update key 0. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doPut(0, txn);
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, false);

        closeEnv(true);
    }

    public void testInsertUpdateAbort()
        throws DatabaseException {

        openEnv();

        /* Insert and update key 0. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doPut(0, txn);
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testInsertUpdateDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert and update {0, 2}. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn);    // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        long file5 = doUpdate(0, 2, txn); // 4rd LN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, false);
        expectObsolete(file5, false);

        closeEnv(true);
    }

    public void testInsertUpdateDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert and update {0, 2}. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn);    // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        long file5 = doUpdate(0, 2, txn); // 4rd LN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, true);

        closeEnv(true);
    }

    public void testInsertDelete()
        throws DatabaseException {

        openEnv();

        /* Insert and update key 0. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doDelete(0, txn);
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testInsertDeleteAbort()
        throws DatabaseException {

        openEnv();

        /* Insert and update key 0. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doDelete(0, txn);
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);

        closeEnv(true);
    }

    public void testInsertDeleteDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert and delete {0, 2}. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn);    // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        long file5 = doDelete(0, 2, txn); // 4rd LN
        long file6 = file5 + 1;           // DupCountLN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, true);
        expectObsolete(file6, false);

        closeEnv(true);
    }

    public void testInsertDeleteDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Insert and delete {0, 2} and abort. */
        txn = env.beginTransaction(null, null);
        long file3 = doPut(0, 2, txn);    // 3rd LN
        long file4 = file3 + 1;           // DupCountLN
        long file5 = doDelete(0, 2, txn); // 4rd LN
        long file6 = file5 + 1;           // DupCountLN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, true);
        expectObsolete(file6, true);

        closeEnv(true);
    }

    public void testUpdateUpdate()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update key 0 twice. */
        Transaction txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        long file2 = doPut(0, txn);
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);
        expectObsolete(file2, false);

        closeEnv(true);
    }

    public void testUpdateUpdateAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update key 0 twice and abort. */
        Transaction txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        long file2 = doPut(0, txn);
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, true);

        closeEnv(true);
    }

    public void testUpdateUpdateDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update {0, 1} twice. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 1, txn); // 3rd LN
        long file4 = doUpdate(0, 1, txn); // 4rd LN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, true);
        expectObsolete(file3, true);
        expectObsolete(file4, false);

        closeEnv(true);
    }

    public void testUpdateUpdateDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update {0, 1} twice and abort. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 1, txn); // 3rd LN
        long file4 = doUpdate(0, 1, txn); // 4rd LN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);

        closeEnv(true);
    }

    public void testUpdateDelete()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update and delete key 0. */
        Transaction txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        long file2 = doDelete(0, txn);
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, true);
        expectObsolete(file1, true);
        expectObsolete(file2, true);

        closeEnv(true);
    }

    public void testUpdateDeleteAbort()
        throws DatabaseException {

        openEnv();

        /* Insert key 0 and checkpoint. */
        long file0 = doPut(0, true);
        env.checkpoint(forceConfig);

        /* Update and delete key 0 and abort. */
        Transaction txn = env.beginTransaction(null, null);
        long file1 = doPut(0, txn);
        long file2 = doDelete(0, txn);
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, true);

        closeEnv(true);
    }

    public void testUpdateDeleteDup()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update and delete {0, 1}. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 1, txn); // 3rd LN
        long file4 = doDelete(0, 1, txn); // 4rd LN
        long file5 = file4 + 1;           // DupCountLN
        txn.commit();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, true);
        expectObsolete(file2, true);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, false);

        closeEnv(true);
    }

    public void testUpdateDeleteDupAbort()
        throws DatabaseException {

        dups = true;
        openEnv();

        /* Insert two key 0 dups and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, 0, txn); // 1st LN
        long file2 = doPut(0, 1, txn); // 2nd LN
        long file1 = file2 - 1;        // DupCountLN
        txn.commit();
        env.checkpoint(forceConfig);

        /* Update and delete {0, 1} and abort. */
        txn = env.beginTransaction(null, null);
        long file3 = doUpdate(0, 1, txn); // 3rd LN
        long file4 = doDelete(0, 1, txn); // 4rd LN
        long file5 = file4 + 1;           // DupCountLN
        txn.abort();
        performRecoveryOperation();

        expectObsolete(file0, false);
        expectObsolete(file1, false);
        expectObsolete(file2, false);
        expectObsolete(file3, true);
        expectObsolete(file4, true);
        expectObsolete(file5, true);

        closeEnv(true);
    }

    public void testTruncate()
        throws DatabaseException {

        truncateOrRemove(true, true);
    }

    public void testTruncateAbort()
        throws DatabaseException {

        truncateOrRemove(true, false);
    }

    public void testRemove()
        throws DatabaseException {

        truncateOrRemove(false, true);
    }

    public void testRemoveAbort()
        throws DatabaseException {

        truncateOrRemove(false, false);
    }

    /**
     */
    private void truncateOrRemove(boolean truncate, boolean commit)
        throws DatabaseException {

        openEnv();

        /* Insert 3 keys and checkpoint. */
        Transaction txn = env.beginTransaction(null, null);
        long file0 = doPut(0, txn);
        long file1 = doPut(1, txn);
        long file2 = doPut(2, txn);
        txn.commit();
        env.checkpoint(forceConfig);

        /* Truncate. */
        txn = env.beginTransaction(null, null);
        if (truncate) {
            db.close();
            db = null;
            long count = env.truncateDatabase(txn, DB_NAME,
                                              true /* returnCount */);
            assertEquals(3, count);
        } else {
            db.close();
            db = null;
            env.removeDatabase(txn, DB_NAME);
        }
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
        truncateOrRemoveDone = true;
        performRecoveryOperation();

        /*
         * Do not check DbFileSummary when we truncate/remove, since the old
         * DatabaseImpl is gone.
         */
        expectObsolete(file0, commit, !commit /*checkDbFileSummary*/);
        expectObsolete(file1, commit, !commit /*checkDbFileSummary*/);
        expectObsolete(file2, commit, !commit /*checkDbFileSummary*/);

        closeEnv(true);
    }

    /*
     * The xxxForceTreeWalk tests set the DatabaseImpl
     * forceTreeWalkForTruncateAndRemove field to true, which will force a walk
     * of the tree to count utilization during truncate/remove, rather than
     * using the per-database info.  This is used to test the "old technique"
     * for counting utilization, which is now used only if the database was
     * created prior to log version 6.
     */

    public void testTruncateForceTreeWalk()
        throws Exception {

        DatabaseImpl.forceTreeWalkForTruncateAndRemove = true;
        try {
            testTruncate();
        } finally {
            DatabaseImpl.forceTreeWalkForTruncateAndRemove = false;
        }
    }

    public void testTruncateAbortForceTreeWalk()
        throws Exception {

        DatabaseImpl.forceTreeWalkForTruncateAndRemove = true;
        try {
            testTruncateAbort();
        } finally {
            DatabaseImpl.forceTreeWalkForTruncateAndRemove = false;
        }
    }

    public void testRemoveForceTreeWalk()
        throws Exception {

        DatabaseImpl.forceTreeWalkForTruncateAndRemove = true;
        try {
            testRemove();
        } finally {
            DatabaseImpl.forceTreeWalkForTruncateAndRemove = false;
        }
    }

    public void testRemoveAbortForceTreeWalk()
        throws Exception {

        DatabaseImpl.forceTreeWalkForTruncateAndRemove = true;
        try {
            testRemoveAbort();
        } finally {
            DatabaseImpl.forceTreeWalkForTruncateAndRemove = false;
        }
    }

    private void expectObsolete(long file, boolean obsolete)
        throws DatabaseException {

        expectObsolete(file, obsolete, true /*checkDbFileSummary*/);
    }

    private void expectObsolete(long file,
                                boolean obsolete,
                                boolean checkDbFileSummary)
        throws DatabaseException {

        FileSummary fileSummary = getFileSummary(file);
        assertEquals("totalLNCount",
                     1, fileSummary.totalLNCount);
        assertEquals("obsoleteLNCount",
                     obsolete ? 1 : 0, fileSummary.obsoleteLNCount);

        DbFileSummary dbFileSummary = getDbFileSummary(file);
        if (checkDbFileSummary) {
            assertEquals("db totalLNCount",
                         1, dbFileSummary.totalLNCount);
            assertEquals("db obsoleteLNCount",
                         obsolete ? 1 : 0, dbFileSummary.obsoleteLNCount);
        }

        if (obsolete) {
            if (expectAccurateObsoleteLNSize()) {
                assertTrue(fileSummary.obsoleteLNSize > 0);
                assertEquals(1, fileSummary.obsoleteLNSizeCounted);
                if (checkDbFileSummary) {
                    assertTrue(dbFileSummary.obsoleteLNSize > 0);
                    assertEquals(1, dbFileSummary.obsoleteLNSizeCounted);
                }
            }
            /* If we counted the size, make sure it is the actual LN size. */
            if (fileSummary.obsoleteLNSize > 0) {
                assertEquals(getLNSize(file), fileSummary.obsoleteLNSize);
            }
            if (checkDbFileSummary) {
                if (dbFileSummary.obsoleteLNSize > 0) {
                    assertEquals(getLNSize(file), dbFileSummary.obsoleteLNSize);
                }
                assertEquals(fileSummary.obsoleteLNSize > 0,
                             dbFileSummary.obsoleteLNSize > 0);
            }
        } else {
            assertEquals(0, fileSummary.obsoleteLNSize);
            assertEquals(0, fileSummary.obsoleteLNSizeCounted);
            if (checkDbFileSummary) {
                assertEquals(0, dbFileSummary.obsoleteLNSize);
                assertEquals(0, dbFileSummary.obsoleteLNSizeCounted);
            }
        }
    }

    /**
     * If an LN is obsolete, expect the size to be counted unless we ran
     * recovery and we did NOT configure fetchObsoleteSize=true.  In that
     * case, the size may or may not be counted depending on how the redo
     * or undo was processed during reocvery.
     */
    private boolean expectAccurateObsoleteLNSize() {
        return fetchObsoleteSize || !OP_RECOVER.equals(operation);
    }

    private long doPut(int key, boolean commit)
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        long file = doPut(key, txn);
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
        return file;
    }

    private long doPut(int key, Transaction txn)
        throws DatabaseException {

        return doPut(key, key, txn);
    }

    private long doPut(int key, int data, Transaction txn)
        throws DatabaseException {

        Cursor cursor = db.openCursor(txn, null);
        IntegerBinding.intToEntry(key, keyEntry);
        IntegerBinding.intToEntry(data, dataEntry);
        cursor.put(keyEntry, dataEntry);
        long file = getFile(cursor);
        cursor.close();
        return file;
    }

    private long doUpdate(int key, int data, Transaction txn)
        throws DatabaseException {

        Cursor cursor = db.openCursor(txn, null);
        IntegerBinding.intToEntry(key, keyEntry);
        IntegerBinding.intToEntry(data, dataEntry);
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchBoth(keyEntry, dataEntry, null));
        cursor.putCurrent(dataEntry);
        long file = getFile(cursor);
        cursor.close();
        return file;
    }

    private long doDelete(int key, boolean commit)
        throws DatabaseException {

        Transaction txn = env.beginTransaction(null, null);
        long file = doDelete(key, txn);
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
        return file;
    }

    private long doDelete(int key, Transaction txn)
        throws DatabaseException {

        Cursor cursor = db.openCursor(txn, null);
        IntegerBinding.intToEntry(key, keyEntry);
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchKey(keyEntry, dataEntry, null));
        cursor.delete();
        long file = getFile(cursor);
        cursor.close();
        return file;
    }

    private long doDelete(int key, int data, Transaction txn)
        throws DatabaseException {

        Cursor cursor = db.openCursor(txn, null);
        IntegerBinding.intToEntry(key, keyEntry);
        IntegerBinding.intToEntry(data, dataEntry);
        assertEquals(OperationStatus.SUCCESS,
                     cursor.getSearchBoth(keyEntry, dataEntry, null));
        cursor.delete();
        long file = getFile(cursor);
        cursor.close();
        return file;
    }

    /**
     * Checkpoint, recover, or do neither, depending on the configured
     * operation for this test.  Always compress to count deleted LNs.
     */
    private void performRecoveryOperation()
        throws DatabaseException {

        if (OP_NONE.equals(operation)) {
            /* Compress to count deleted LNs. */
            env.compress();
        } else if (OP_CHECKPOINT.equals(operation)) {
            /* Compress before checkpointing to count deleted LNs. */
            env.compress();
            env.checkpoint(forceConfig);
        } else if (OP_RECOVER.equals(operation)) {
            closeEnv(false);
            openEnv();
            /* Compress after recovery to count deleted LNs. */
            env.compress();
        } else {
            assert false : operation;
        }
    }

    /**
     * Gets the file of the LSN at the cursor position, using internal methods.
     * Also check that the file number is greater than the last file returned,
     * to ensure that we're filling a file every time we write.
     */
    private long getFile(Cursor cursor) {
        long file = CleanerTestUtils.getLogFile(this, cursor);
        assert file > lastFileSeen;
        lastFileSeen = file;
        return file;
    }

    /**
     * Returns the utilization summary for a given log file.
     */
    private FileSummary getFileSummary(long file) {
        return envImpl.getUtilizationProfile()
               .getFileSummaryMap(true)
               .get(new Long(file));
    }

    /**
     * Returns the per-database utilization summary for a given log file.
     */
    private DbFileSummary getDbFileSummary(long file) {
        return dbImpl.getDbFileSummary
            (new Long(file), false /*willModify*/);
    }

    /**
     * Peek into the file to get the total size of the first entry past the
     * file header, which is known to be the LN log entry.
     */
    private int getLNSize(long file)
        throws DatabaseException {

        try {
            long offset = FileManager.firstLogEntryOffset();
            long lsn = DbLsn.makeLsn(file, offset);
            LogManager lm = envImpl.getLogManager();
            LogSource src = lm.getLogSource(lsn);
            ByteBuffer buf = src.getBytes(offset);
            LogEntryHeader header =
                new LogEntryHeader(buf, LogEntryType.LOG_VERSION);
            int size = header.getItemSize();
            src.release();
            return size + header.getSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ChecksumException e) {
            throw new RuntimeException(e);
        }
    }
}
