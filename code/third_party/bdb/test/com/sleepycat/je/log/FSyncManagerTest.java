/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: FSyncManagerTest.java,v 1.19 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.util.TestUtils;

/**
 * Exercise the synchronization aspects of the sync manager.
 */
public class FSyncManagerTest extends TestCase {
    private final File envHome;

    public FSyncManagerTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    protected void setUp() {
        /* Remove files to start with a clean slate. */
        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    @Override
    protected void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testBasic()
        throws Throwable{
        Environment env = null;

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setConfigParam(EnvironmentParams.LOG_FSYNC_TIMEOUT.getName(),
                                     "50000000");
            envConfig.setAllowCreate(true);
            env = new Environment(envHome, envConfig);

            WaitVal waitVal = new WaitVal(0);

            FSyncManager syncManager =
                new TestSyncManager(DbInternal.getEnvironmentImpl(env),
                                    waitVal);
            JUnitThread t1 = new TestSyncThread(syncManager);
            JUnitThread t2 = new TestSyncThread(syncManager);
            JUnitThread t3 = new TestSyncThread(syncManager);
            t1.start();
            t2.start();
            t3.start();

            /* Wait for all threads to request a sync, so they form a group.*/
            Thread.sleep(500);

            /* Free thread 1. */
            synchronized (waitVal) {
                waitVal.value = 1;
                waitVal.notify();
            }

            t1.join();
            t2.join();
            t3.join();

            /*
             * All three threads ask for fsyncs.
             * 2 do fsyncs -- the initial leader, and the leader of the
             * waiting group of 2.
             * The last thread gets a free ride.
             */
            assertEquals(3, syncManager.getNFSyncRequests());
            assertEquals(2, syncManager.getNFSyncs());
            assertEquals(0, syncManager.getNTimeouts());
        } finally {
            if (env != null) {
                env.close();
            }
        }
    }

    /* This test class waits for an object instead of executing a sync.
     * This way, we can manipulate grouping behavior.
     */
    class TestSyncManager extends FSyncManager {
        private final WaitVal waitVal;
        TestSyncManager(EnvironmentImpl env, WaitVal waitVal) {
            super(env);
            this.waitVal = waitVal;
        }
        @Override
        protected void executeFSync() {
            try {
                synchronized (waitVal) {
                    if (waitVal.value < 1) {
                        waitVal.wait();
                    }
                }
            } catch (InterruptedException e) {
                // woken up.
            }
        }
    }

    class TestSyncThread extends JUnitThread {
        private final FSyncManager syncManager;
        TestSyncThread(FSyncManager syncManager) {
            super("syncThread");
            this.syncManager = syncManager;
        }

        @Override
        public void testBody()
            throws Throwable {
            syncManager.fsync();
        }
    }

    class WaitVal {
        public int value;

        WaitVal(int value) {
            this.value = value;
        }
    }
}
