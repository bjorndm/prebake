/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStatTest.java,v 1.32 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.util.TestUtils;

public class EnvironmentStatTest extends TestCase {

    private Environment env;
    private final File envHome;
    private static final String DB_NAME = "foo";

    public EnvironmentStatTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        if (env != null) {
            env.close();
        }

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Test open and close of an environment.
     */
    public void testCacheStats()
        throws Exception {

        /* Init the Environment. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        EnvironmentStats stat = env.getStats(TestUtils.FAST_STATS);
        env.close();
        assertEquals(0, stat.getNCacheMiss());
        assertEquals(0, stat.getNNotResident());

        // Try to open and close again, now that the environment exists
        envConfig.setAllowCreate(false);
        env = new Environment(envHome, envConfig);

        /* Open a database and insert some data. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, DB_NAME, dbConfig);
        db.put(null, new DatabaseEntry(new byte[0]),
                     new DatabaseEntry(new byte[0]));
        Transaction txn = env.beginTransaction(null, null);
        db.put(txn, new DatabaseEntry(new byte[0]),
                    new DatabaseEntry(new byte[0]));

        /* Do the check. */
        stat = env.getStats(TestUtils.FAST_STATS);
        MemoryBudget mb =
            DbInternal.getEnvironmentImpl(env).getMemoryBudget();

        assertEquals(mb.getCacheMemoryUsage(), stat.getCacheTotalBytes());
        assertEquals(mb.getLogBufferBudget(), stat.getBufferBytes());
        assertEquals(mb.getTreeMemoryUsage() + mb.getTreeAdminMemoryUsage(),
                     stat.getDataBytes());
        assertEquals(mb.getLockMemoryUsage(), stat.getLockBytes());
        assertEquals(mb.getAdminMemoryUsage(), stat.getAdminBytes());

        assertTrue(stat.getBufferBytes() > 0);
        assertTrue(stat.getDataBytes() > 0);
        assertTrue(stat.getLockBytes() > 0);
        assertTrue(stat.getAdminBytes() > 0);

        assertEquals(stat.getCacheTotalBytes(),
                     stat.getBufferBytes() +
                     stat.getDataBytes() +
                     stat.getLockBytes() +
                     stat.getAdminBytes());

        assertEquals(11, stat.getNCacheMiss());
        assertEquals(11, stat.getNNotResident());

        /* Test deprecated getCacheDataBytes method. */
        final EnvironmentStats finalStat = stat;
        final long expectCacheDataBytes = mb.getCacheMemoryUsage() -
                                          mb.getLogBufferBudget();
        (new Runnable() {
            @Deprecated
            public void run() {
                assertEquals(expectCacheDataBytes,
                             finalStat.getCacheDataBytes());
            }
        }).run();

        txn.abort();
        db.close();
    }
}
