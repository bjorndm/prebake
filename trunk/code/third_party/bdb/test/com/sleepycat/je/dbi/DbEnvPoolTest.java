/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: DbEnvPoolTest.java,v 1.20 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

public class DbEnvPoolTest extends TestCase {

    private final File envHome = new File(System.getProperty(TestUtils.DEST_DIR));

    public DbEnvPoolTest() {
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    @Override
    public void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testCanonicalEnvironmentName ()
        throws Throwable {

        try {
            File file2 = new File("build/test/classes");

            /* Create an environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            Environment envA = new Environment(envHome, envConfig);

            /* Look in the environment pool with the relative path name. */
            EnvironmentImpl envImpl =
                DbEnvPool.getInstance().getEnvironment
                    (file2, TestUtils.initEnvConfig(),
                     false /*checkImmutableParams*/,
                     false /*openIfNeeded*/,
                     null  /* repInstance */);
            /* We should find this file in the pool without opening it. */
            assertNotNull(envImpl);
            envImpl.decReferenceCount();
            envA.close();

        } catch (Throwable t) {
            /* Dump stack trace before trying to tear down. */
            t.printStackTrace();
            throw t;
        }
    }
}
