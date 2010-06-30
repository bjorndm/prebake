/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: RemoteProcessingTest.java,v 1.8 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util.ldiff;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

public class RemoteProcessingTest extends TestCase {
    private File envHome;
    private final int dbCount = 25000;
    private final String DB_NAME = "testDb";

    /* The remote database environment. */
    private static Environment env;
    /* The list of blocks constituting RDB. */
    static final List<Block> rbList = new ArrayList<Block>();

    public RemoteProcessingTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() throws Exception {

        TestUtils.removeLogFiles("Setup", envHome, false);
        fillData();
    }

    public void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    private void fillData() throws Exception {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);

        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);

        Database db = env.openDatabase(null, DB_NAME, dbConfig);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 1; i <= dbCount; i++) {
            IntegerBinding.intToEntry(i, key);
            StringBinding.stringToEntry("bdb je", data);
            db.put(null, key, data);
        }
        db.close();
        env.close();
    }

    public static void configure(String envDir) {
        env = LDiffUtil.openEnv(envDir);
    }

    public void testPlaceHolder() {
        /* 
         * A Junit test will fail if there are no tests cases at all, so
         * here is a placeholder test.
         */
    }
}
