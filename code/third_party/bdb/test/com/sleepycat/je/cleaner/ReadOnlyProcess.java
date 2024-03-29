/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReadOnlyProcess.java,v 1.12 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

/**
 * @see ReadOnlyLockingTest
 */
public class ReadOnlyProcess {

    public static void main(String[] args) {

        /*
         * Don't write to System.out in this process because the parent
         * process only reads System.err.
         */
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setReadOnly(true);

            File envHome = new File(System.getProperty(TestUtils.DEST_DIR));
            Environment env = new Environment(envHome, envConfig);

            //System.err.println("Opened read-only: " + envHome);
            //System.err.println(System.getProperty("java.class.path"));

            /* Notify the test that this process has opened the environment. */
            ReadOnlyLockingTest.createProcessFile();

            /* Sleep until the parent process kills me. */
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {

            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
