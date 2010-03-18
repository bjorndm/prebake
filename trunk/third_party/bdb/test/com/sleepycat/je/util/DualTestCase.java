/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DualTestCase.java,v 1.8 2010/01/04 15:51:08 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

public abstract class DualTestCase extends TestCase {

    /* All environment management APIs are forwarded to this wrapper. */
    private EnvTestWrapper envWrapper;

    /* Helps determine whether setUp()and tearDown() were invoked as a pair */
    private boolean setUpInvoked = false;

    public DualTestCase() {
        super();
    }

    protected DualTestCase(String name) {
        super(name);
    }

    @Override
    protected void setUp()
        throws Exception {

        setUpInvoked = true;
        super.setUp();
        if (DualTestCase.isReplicatedTest(getClass())) {
            try {
                /* Load the class dynamically to avoid a dependency */
                Class<?> cl =
                    Class.forName("com.sleepycat.je.rep.util.RepEnvWrapper");
                envWrapper = (EnvTestWrapper) cl.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            envWrapper = new EnvTestWrapper.LocalEnvWrapper();
        }
    }

    @Override
    protected void tearDown()
        throws Exception {

        if (!setUpInvoked) {
            throw new IllegalStateException
                ("DualTestCase.tearDown was invoked without a corresponding " +
                 "DualTestCase.setUp() call");
        }
        envWrapper.destroy();
        super.tearDown();
    }

    /**
     * Creates the environment to be used by the test case. If the environment
     * already exists on disk, it reuses it. If not, it creates a new
     * environment and returns it.
     */
    protected Environment create(File envHome,
                                 EnvironmentConfig envConfig)
        throws DatabaseException {

        return envWrapper.create(envHome, envConfig);
    }

    /**
     * Closes the environment.
     *
     * @param environment the environment to be closed.
     *
     * @throws DatabaseException
     */
    protected void close(Environment environment)
        throws DatabaseException {

        envWrapper.close(environment);
    }

    protected void resetNodeEqualityCheck() {
        envWrapper.resetNodeEqualityCheck();
    }

    /**
     * Closes the environment without a checkpoint.
     *
     * @param environment the environment to be closed.
     *
     * @throws DatabaseException
     */
    protected void closeNoCheckpoint(Environment environment)
        throws DatabaseException {

        envWrapper.closeNoCheckpoint(environment);
    }

    /**
     * Destroys the contents of the test directory used to hold the test
     * environments.
     *
     * @throws Exception
     */
    protected void destroy()
        throws Exception {

        envWrapper.destroy();
    }

    /**
     * Determines whether this test is to be run with a replicated environment.
     * If the test is in the "rep" package it assumes that the test is to be
     * run in a replicated environment.
     *
     * It's used to bypass the specifics of tests that may not be suitable for
     * replication, e.g. non-transactional mode testing.
     *
     * @param testCaseClass the test case class
     * @return true if the test uses a replicated environment, false otherwise.
     */
    public static boolean isReplicatedTest(Class<?> testCaseClass) {
        return testCaseClass.getName().contains(".rep.");
    }
}
