/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SR18227Test.java,v 1.1 2010/01/29 17:34:03 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;

public class SR18227Test extends TestCase {

    private static final String DB_NAME = "foo";

    private File envHome;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private JUnitThread junitThread;
    private boolean deferredWrite;

    public SR18227Test() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        if (junitThread != null) {
            while (junitThread.isAlive()) {
                junitThread.interrupt();
                Thread.yield();
            }
            junitThread = null;
        }

        try {
            if (env != null) {
                env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        //*
        try {
            TestUtils.removeLogFiles("tearDown", envHome, true);
            TestUtils.removeFiles("tearDown", envHome, FileManager.DEL_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
        //*/

        db = null;
        env = null;
        envImpl = null;
        envHome = null;
    }

    /**
     * Opens the environment and database.
     */
    private void openEnv() {

        EnvironmentConfig config = TestUtils.initEnvConfig();
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

        /* Use a small cache size to increase eviction. */
        config.setConfigParam(EnvironmentParams.MAX_MEMORY.getName(),
                              Integer.toString(1024 * 96));

        /*
         * Disable critical eviction, we want to test under controlled
         * circumstances.
         */
        config.setConfigParam
            (EnvironmentParams.EVICTOR_CRITICAL_PERCENTAGE.getName(), "1000");

        env = new Environment(envHome, config);
        envImpl = DbInternal.getEnvironmentImpl(env);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        if (deferredWrite) {
            dbConfig.setDeferredWrite(true);
        } else {
            dbConfig.setTemporary(true);
        }
        db = env.openDatabase(null, DB_NAME, dbConfig);
    }

    /**
     * Closes the environment and database.
     */
    private void closeEnv() {

        if (db != null) {
            db.close();
            db = null;
        }
        if (env != null) {
            env.close();
            env = null;
        }
    }

    /**
     * Tests additionally with deferred-write instead of a temporary database
     * as a double-check that the test is correct and that the problem is
     * limited to temporary DBs.
     */
    public void testDeferredWrite() {
        deferredWrite = true;
        testSR18227();
    }

    /**
     * Tests a fix for a bug where a BIN was evicted, without flushing it, when
     * it contained a LN that had been dirtied by log cleaning.
     */
    public void testSR18227() {

        openEnv();

        /*
         * Insert many records to cause eviction of BINs.  Critical eviction is
         * disabled, so no eviction occurs until evictMemory is invoked.
         */
        final int RECORD_COUNT = 100000;
        final DatabaseEntry key = new DatabaseEntry();
        final DatabaseEntry data = new DatabaseEntry(new byte[100]);
        for (int i = 0; i < RECORD_COUNT; i += 1) {
            IntegerBinding.intToEntry(i, key);
            db.put(null, key, data);
        }
        /* Evict to flush data to disk, then load again. */
        env.evictMemory();
        for (int i = 0; i < RECORD_COUNT; i += 1) {
            IntegerBinding.intToEntry(i, key);
            db.get(null, key, data, null);
        }

        final AtomicReference<BIN> foundBin = new AtomicReference<BIN>(null);
        final AtomicLong foundLsn = new AtomicLong(DbLsn.NULL_LSN);
        final AtomicInteger foundLn = new AtomicInteger(-1);

        /* Simulate processing of an LN in the log cleaner. */
        junitThread = new JUnitThread("testSR18227") {
            public void testBody() {
                final BIN bin = foundBin.get();
                assertNotNull(bin);
                final int index = foundLn.get();
                assertTrue(index >= 0);

                final FileProcessor processor = new FileProcessor
                    ("testSR18227", envImpl, envImpl.getCleaner(),
                     envImpl.getUtilizationProfile(),
                     envImpl.getCleaner().getFileSelector());

                final Map<DatabaseId, DatabaseImpl> dbCache =
                    new HashMap<DatabaseId, DatabaseImpl>();
                try {
                    processor.testProcessLN
                        ((LN) bin.getTarget(index), bin.getLsn(index),
                         bin.getKey(index), null /*dupKey*/,
                         bin.getDatabase().getId(), dbCache);
                } finally {
                    envImpl.getDbTree().releaseDbs(dbCache);
                }
            }
        };

        /*
         * When an IN is about to be evicted, get control while it is latched
         * but before the evictor re-searches for the parent IN.
         */
        final TestHook preEvictINHook = new TestHook() {
            public void doHook() {
                if (foundLn.get() >= 0) {
                    return;
                }
                assertEquals(1, LatchSupport.countLatchesHeld());
                final BIN bin = findNonDirtyLatchedBIN();
                if (bin != null) {
                    foundBin.set(bin);
                    foundLsn.set(bin.getLastFullVersion());
                    final int index = findDurableLN(bin);
                    if (index >= 0) {
                        foundLn.set(index);
                        final LN ln = (LN) bin.fetchTarget(index);
                        assertNotNull(ln);
                        final IN parent = findBINParent(bin);
                        if (parent.latchNoWait()) {
                            parent.releaseLatch();
                        } else {
                            fail("Parent should not currently be latched.");
                        }
                        junitThread.start();

                        /*
                         * Loop until BIN parent is latched by cleaner in
                         * separate thread.  When this occurs, the cleaner will
                         * then try to latch the BIN itself.
                         */
                        while (junitThread.isAlive()) {
                            if (parent.latchNoWait()) {
                                parent.releaseLatch();
                                Thread.yield();
                            } else {
                                break;
                            }
                        }

                        /*
                         * Perform one final yield to ensure that the cleaner
                         * has time to request the latch on the BIN.
                         */
                        Thread.yield();
                        assertEquals(1, LatchSupport.countLatchesHeld());
                    }
                }
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
        };

        /*
         * Set the pre-eviction hook and start eviction in this thread.  When
         * evictMemory is called, that sets off the following sequence of
         * events using the thread and hook defined further above.
         *
         * 1. The evictor (in this thread) will select a BIN for eviction.
         * 2. The hook (above) will choose a BIN that is selected by evictor
         *    (it determines this by finding the BIN that is latched).  It is
         *    looking for a BIN in the temp DB that is non-dirty.
         * 3. The hook starts the separate thread to simulate processing of the
         *    LN by the log cleaner.
         * 4. When the log cleaner (separate thread) has latched the BIN's
         *    parent and is attemping to latch the BIN, the hook returns to
         *    allow the evictor to continue.
         * 5. The evictor then releases the latch on the BIN, in order to
         *    re-search for it's parent.  By releasing the BIN latch, the
         *    separate thread is then activated, since it was waiting on a
         *    latch request for that BIN.
         * 6. The separate thread then marks the LN in the BIN dirty.  The bug
         *    is that it neglected to mark the BIN dirty.  This thread then
         *    finishes.
         * 7. The evictor now continues because it can get the latch on the
         *    BIN.  When the bug was present, it would NOT flush the BIN,
         *    because it was not dirty.  With the bug fix, the BIN is now
         *    dirtied by the cleaner, and the evictor will flush it.
         */
        envImpl.getEvictor().setPreEvictINHook(preEvictINHook);
        env.evictMemory();

        /* Ensure separate thread is finished and report any exceptions. */
        try {
            junitThread.finishTest();
            junitThread = null;
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        }

        /*
         * After that entire process is complete, we can check that it did what
         * we expected, and the BIN was flushed by the evictor.
         */
        final BIN bin = foundBin.get();
        assertNotNull(bin);
        final int index = foundLn.get();
        assertTrue(index >= 0);
        /* Ensure the BIN was evicted. */
        assertFalse(envImpl.getInMemoryINs().contains(bin));
        /* Ensure the BIN was flushed: this failed before the bug fix. */
        assertTrue(bin.getLastFullVersion() != foundLsn.get());
        /* Ensure the dirty LN was written. */
        final LN ln = (LN) bin.getTarget(index);
        assertNotNull(ln);
        assertFalse(ln.isDirty());
        assertTrue(DbLsn.NULL_LSN != bin.getLsn(index));

        closeEnv();
    }

    private BIN findNonDirtyLatchedBIN() {
        for (IN in : envImpl.getInMemoryINs()) {
            if (in.isLatchOwnerForWrite()) {
                if (in.getDatabase() != DbInternal.getDatabaseImpl(db)) {
                    return null;
                }
                if (!(in instanceof BIN)) {
                    return null;
                }
                BIN bin = (BIN) in;
                if (bin.getDirty()) {
                    return null;
                }
                return bin;
            }
        }
        fail("No IN latched");
        return null; // for compiler
    }

    private IN findBINParent(BIN bin) {
        for (IN in : envImpl.getInMemoryINs()) {
            if (in.getLevel() != IN.BIN_LEVEL + 1) {
                continue;
            }
            for (int i = 0; i < in.getNEntries(); i += 1) {
                if (in.getTarget(i) == bin) {
                    return in;
                }
            }
        }
        fail("No BIN parent");
        return null; // for compiler
    }

    private int findDurableLN(BIN bin) {
        for (int i = 0; i < bin.getNEntries(); i += 1) {
            if (bin.getLsn(i) != DbLsn.NULL_LSN) {
                return i;
            }
        }
        return -1;
    }
}
