/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: BackgroundIOTest.java,v 1.17 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.TestHook;

public class BackgroundIOTest extends TestCase {

    final static int FILE_SIZE = 1000000;

    private static CheckpointConfig forceConfig;
    static {
        forceConfig = new CheckpointConfig();
        forceConfig.setForce(true);
    }

    private final File envHome;
    private Environment env;
    private int readLimit;
    private int writeLimit;
    private int nSleeps;

    public BackgroundIOTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        if (env != null) {
            try {
                env.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
            env = null;
        }

        //*
        TestUtils.removeLogFiles("TearDown", envHome, true);
        TestUtils.removeFiles("TearDown", envHome, FileManager.DEL_SUFFIX);
        //*/
    }

    public void testBackgroundIO1()
        throws DatabaseException {

        openEnv(10, 10);
        if (isCkptHighPriority()) {
            doTest(93, 113);
        } else {
            doTest(186, 206);
        }
    }

    public void testBackgroundIO2()
        throws DatabaseException {

        openEnv(10, 5);
        if (isCkptHighPriority()) {
            doTest(93, 113);
        } else {
            doTest(310, 330);
        }
    }

    public void testBackgroundIO3()
        throws DatabaseException {

        openEnv(5, 10);
        if (isCkptHighPriority()) {
            doTest(167, 187);
        } else {
            doTest(259, 279);
        }
    }

    public void testBackgroundIO4()
        throws DatabaseException {

        openEnv(5, 5);
        if (isCkptHighPriority()) {
            doTest(167, 187);
        } else {
            doTest(383, 403);
        }
    }

    private boolean isCkptHighPriority()
        throws DatabaseException {

        return "true".equals(env.getConfig().getConfigParam
            (EnvironmentParams.CHECKPOINTER_HIGH_PRIORITY.getName()));
    }

    private void openEnv(int readLimit, int writeLimit)
        throws DatabaseException {

        this.readLimit = readLimit;
        this.writeLimit = writeLimit;

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_BUFFER_MAX_SIZE.getName(),
             Integer.toString(1024));
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(FILE_SIZE));
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "60");
        //*
        envConfig.setConfigParam
            (EnvironmentParams.ENV_BACKGROUND_READ_LIMIT.getName(),
             String.valueOf(readLimit));
        envConfig.setConfigParam
            (EnvironmentParams.ENV_BACKGROUND_WRITE_LIMIT.getName(),
             String.valueOf(writeLimit));
        //*/
        env = new Environment(envHome, envConfig);
    }

    private void doTest(int minSleeps, int maxSleeps)
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        envImpl.setBackgroundSleepHook(new TestHook() {
                public void doHook() {
                    nSleeps += 1;
                    assertEquals(0, LatchSupport.countLatchesHeld());
                }
                public Object getHookValue() {
                    throw new UnsupportedOperationException();
                }
                public void doIOHook() {
                    throw new UnsupportedOperationException();
                }
                public void hookSetup() {
                    throw new UnsupportedOperationException();
                }
            });

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        Database db = env.openDatabase(null, "BackgroundIO", dbConfig);

        final int nFiles = 3;
        final int keySize = 20;
        final int dataSize = 10;
        final int recSize = keySize + dataSize + 35 /* LN overhead */;
        final int nRecords = nFiles * (FILE_SIZE / recSize);

        /*
         * Insert records first so we will have a sizeable checkpoint.  Insert
         * interleaved because sequential inserts flush the BINs, and we want
         * to defer BIN flushing until the checkpoint.
         */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[dataSize]);
        for (int i = 0; i <= nRecords; i += 2) {
            setKey(key, i, keySize);
            db.put(null, key, data);
        }
        for (int i = 1; i <= nRecords; i += 2) {
            setKey(key, i, keySize);
            db.put(null, key, data);
        }

        /* Perform a checkpoint to perform background writes. */
        env.checkpoint(forceConfig);

        /* Delete records so we will have a sizable cleaning. */
        for (int i = 0; i <= nRecords; i += 1) {
            setKey(key, i, keySize);
            db.delete(null, key);
        }

        /* Perform cleaning to perform background reading. */
        env.checkpoint(forceConfig);
        env.cleanLog();
        env.checkpoint(forceConfig);

        db.close();
        env.close();
        env = null;

        String msg;
        msg = "readLimit=" + readLimit +
              " writeLimit=" + writeLimit +
              " minSleeps=" + minSleeps +
              " maxSleeps=" + maxSleeps +
              " actualSleeps=" + nSleeps;
        //System.out.println(msg);

        //*
        assertTrue(msg, nSleeps >= minSleeps && nSleeps <= maxSleeps);
        //*/
    }

    /**
     * Outputs an integer followed by pad bytes.
     */
    private void setKey(DatabaseEntry entry, int val, int len) {
        TupleOutput out = new TupleOutput();
        out.writeInt(val);
        for (int i = 0; i < len - 4; i += 1) {
            out.writeByte(0);
        }
        TupleBase.outputToEntry(out, entry);
    }
}
