/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: KeyScanTest.java,v 1.4 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.test;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;

/**
 */
public class KeyScanTest extends TestCase {

    private File envHome;
    private Environment env;

    public KeyScanTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        try {
            closeEnv();
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

        envHome = null;
        env = null;
    }

    private void openEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        env = new Environment(envHome, envConfig);
    }

    private void closeEnv()
        throws DatabaseException {

        if (env != null) {
            env.close();
            env = null;
        }
    }

    public void testKeyScan() {
        doKeyScan(false /*dups*/);
    }

    public void testKeyScanDup() {
        doKeyScan(true /*dups*/);
    }

    private void doKeyScan(final boolean dups) {
        final DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(dups);
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry data = new DatabaseEntry();
        final int RECORD_COUNT = 3 * 500;
        OperationStatus status;

        /* Open env, write data, close. */
        openEnv();
        Database db = env.openDatabase(null, "foo", dbConfig);
        for (int i = 0; i < RECORD_COUNT; i += 1) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(1, data);
            status = db.putNoOverwrite(null, key, data);
            assertSame(OperationStatus.SUCCESS, status);
            if (dups && ((i % 2) == 1)) {
                IntegerBinding.intToEntry(2, data);
                status = db.putNoDupData(null, key, data);
                assertSame(OperationStatus.SUCCESS, status);
            }
        }
        db.close();
        closeEnv();

        /* Open env, preload without loading LNs. */
        openEnv();
        dbConfig.setAllowCreate(false);
        db = env.openDatabase(null, "foo", dbConfig);
        db.preload(new PreloadConfig());

        /* Clear stats. */
        final StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);
        EnvironmentStats stats = env.getStats(statsConfig);

        /* Key scan with dirty read. */
        for (int variant = 0; variant < 2; variant += 1) {
            LockMode lockMode = null;
            CursorConfig cursorConfig = null;
            switch (variant) {
                case 0:
                    lockMode = LockMode.READ_UNCOMMITTED;
                    break;
                case 1:
                    cursorConfig = CursorConfig.READ_UNCOMMITTED;
                    break;
                default:
                    fail();
            }
            data.setPartial(0, 0, true);
            Cursor c = db.openCursor(null, cursorConfig);
            int count = 0;
            int expectKey = 0;
            if (dups) {
                while (c.getNextNoDup(key, data, lockMode) ==
                       OperationStatus.SUCCESS) {
                    assertEquals(count, IntegerBinding.entryToInt(key));
                    count += 1;
                }
            } else {
                while (c.getNext(key, data, lockMode) ==
                       OperationStatus.SUCCESS) {
                    assertEquals(count, IntegerBinding.entryToInt(key));
                    count += 1;
                }
            }
            assertEquals(RECORD_COUNT, count);

            /* Try other misc operations. */
            status = c.getFirst(key, data, lockMode);
            assertSame(OperationStatus.SUCCESS, status);
            assertEquals(0, IntegerBinding.entryToInt(key));

            status = c.getLast(key, data, lockMode);
            assertSame(OperationStatus.SUCCESS, status);
            assertEquals(RECORD_COUNT - 1, IntegerBinding.entryToInt(key));

            IntegerBinding.intToEntry(RECORD_COUNT / 2, key);
            status = c.getSearchKey(key, data, lockMode);
            assertSame(OperationStatus.SUCCESS, status);
            assertEquals(RECORD_COUNT / 2, IntegerBinding.entryToInt(key));

            IntegerBinding.intToEntry(RECORD_COUNT / 2, key);
            status = c.getSearchKeyRange(key, data, lockMode);
            assertSame(OperationStatus.SUCCESS, status);
            assertEquals(RECORD_COUNT / 2, IntegerBinding.entryToInt(key));

            c.close();

            /* Expect no cache misses. */
            stats = env.getStats(statsConfig);
            assertEquals(0, stats.getNCacheMiss());
            assertEquals(0, stats.getNNotResident());
        }

        db.close();
        closeEnv();
    }
}
