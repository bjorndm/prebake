/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DbTreeTest.java,v 1.35 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.DualTestCase;
import com.sleepycat.je.util.TestUtils;

public class DbTreeTest extends DualTestCase {
    private final File envHome;

    public DbTreeTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() 
        throws Exception {

        super.setUp();

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    @Override
    public void tearDown() 
        throws Exception {

        super.tearDown();
    }

    public void testDbLookup() throws Throwable {
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setAllowCreate(true);
            Environment env = create(envHome, envConfig);

            // Make two databases
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database dbHandleAbcd = env.openDatabase(null, "abcd", dbConfig);
            Database dbHandleXyz = env.openDatabase(null, "xyz", dbConfig);

            // Can we get them back?
            dbConfig.setAllowCreate(false);
            Database newAbcdHandle = env.openDatabase(null, "abcd", dbConfig);
            Database newXyzHandle = env.openDatabase(null, "xyz", dbConfig);

            dbHandleAbcd.close();
            dbHandleXyz.close();
            newAbcdHandle.close();
            newXyzHandle.close();
            close(env);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
