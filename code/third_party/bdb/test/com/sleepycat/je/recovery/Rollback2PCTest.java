/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: Rollback2PCTest.java,v 1.10 2010/01/04 15:51:03 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;

import javax.transaction.xa.XAException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogUtils.XidImpl;
import com.sleepycat.je.util.TestUtils;

public class Rollback2PCTest extends TestCase {
    private final File envHome;

    public Rollback2PCTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        try {
            TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public void tearDown() {
        //*
        try {
            TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        //*/
    }

    /**
     * Test that getXATransaction does not return a prepared txn.
     */
    public void testSR16375()
        throws DatabaseException, XAException {

            /* Setup environment. */
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        XAEnvironment xaEnv = new XAEnvironment(envHome, envConfig);

        /* Setup database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = xaEnv.openDatabase(null, "foo", dbConfig);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(1, key);

        /*
         * Start an XA transaction and add a record.  Then crash the
         * environment.
         */
        XidImpl xid = new XidImpl(1, "FooTxn".getBytes(), null);
        Transaction preCrashTxn = xaEnv.beginTransaction(null, null);
        xaEnv.setXATransaction(xid, preCrashTxn);
        IntegerBinding.intToEntry(99, data);
        assertEquals(OperationStatus.SUCCESS, db.put(preCrashTxn, key, data));
        db.close();
        xaEnv.prepare(xid);
        xaEnv.sync();

        /* Crash */
        DbInternal.getEnvironmentImpl(xaEnv).abnormalClose();
        xaEnv = null;

        /* Recover */
        envConfig.setAllowCreate(false);
        xaEnv = new XAEnvironment(envHome, envConfig);

        /* Ensure that getXATransaction returns null. */
        Transaction resumedTxn = xaEnv.getXATransaction(xid);
        assertNull(resumedTxn);

        /* Rollback. */
        xaEnv.rollback(xid);
        DbInternal.getEnvironmentImpl(xaEnv).abnormalClose();
    }

    /**
     * Verifies a bug fix to a problem that occurs when aborting a prepared txn
     * after recovery.  During recovery, we were counting the old version of an
     * LN as obsolete when replaying the prepared txn LN.  But if that txn
     * aborts later, the old version becomes active.  The fix is to use inexact
     * counting.  [#17022]
     */
    public void testLogCleanAfterRollbackPrepared()
        throws DatabaseException, XAException {

            /* Setup environment. */
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
        envConfig.setConfigParam
            (EnvironmentConfig.CLEANER_MIN_UTILIZATION, "90");
        XAEnvironment xaEnv = new XAEnvironment(envHome, envConfig);

        /* Setup database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = xaEnv.openDatabase(null, "foo", dbConfig);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(1, key);
        IntegerBinding.intToEntry(99, data);
        assertEquals(OperationStatus.SUCCESS, db.put(null, key, data));
        DbInternal.getEnvironmentImpl(xaEnv).forceLogFileFlip();
        DbInternal.getEnvironmentImpl(xaEnv).forceLogFileFlip();
        DbInternal.getEnvironmentImpl(xaEnv).forceLogFileFlip();

        /*
         * Start an XA transaction and add a record.  Then crash the
         * environment.
         */
        XidImpl xid = new XidImpl(1, "FooTxn".getBytes(), null);
        Transaction preCrashTxn = xaEnv.beginTransaction(null, null);
        xaEnv.setXATransaction(xid, preCrashTxn);
        IntegerBinding.intToEntry(100, data);
        assertEquals(OperationStatus.SUCCESS, db.put(preCrashTxn, key, data));
        db.close();
        xaEnv.prepare(xid);
        DbInternal.getEnvironmentImpl(xaEnv).getLogManager().flush();

        /* Crash */
        DbInternal.getEnvironmentImpl(xaEnv).abnormalClose();
        xaEnv = null;

        /* Recover */
        envConfig.setAllowCreate(false);
        xaEnv = new XAEnvironment(envHome, envConfig);

        /* Rollback. */
        xaEnv.rollback(xid);
        
        /* Force log cleaning. */
        CheckpointConfig force = new CheckpointConfig();
        force.setForce(true);
        xaEnv.checkpoint(force);
        xaEnv.cleanLog();
        xaEnv.checkpoint(force);

        /* Close and re-open, ensure we can read the original record. */
        xaEnv.close();
        xaEnv = new XAEnvironment(envHome, envConfig);
        db = xaEnv.openDatabase(null, "foo", dbConfig);
        /* Before the fix, the get() caused a LogFileNotFound. */
        assertEquals(OperationStatus.SUCCESS, db.get(null, key, data, null));
        assertEquals(99, IntegerBinding.entryToInt(data));
        db.close();
        xaEnv.close();
    }
}
