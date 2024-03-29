/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ConversionTest.java,v 1.17 2010/01/04 15:51:04 cwl Exp $
 */
package com.sleepycat.je.rep;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.CmdUtil;

/**
 * Standalone environments can be converted once to be replicated environments.
 * Replicated environments can't be opened in standalone mode.
 */
public class ConversionTest extends TestCase {

    private final File envRoot;

    public ConversionTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    public void tearDown() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    /**
     * Check that an environment which is opened for replication cannot be
     * re-opened as a standalone environment in r/w mode
     */
    public void testNoStandaloneReopen()
        throws DatabaseException, IOException {

        RepEnvInfo[] repEnvInfo = initialOpenWithReplication();

        /* Try to re-open standalone r/w, should fail. */
        try {
            EnvironmentConfig reopenConfig = new EnvironmentConfig();
            reopenConfig.setTransactional(true);
            @SuppressWarnings("unused")
            Environment unused = new Environment(repEnvInfo[0].getEnvHome(),
                                                 reopenConfig);
            fail("Should have thrown an exception.");
        } catch (UnsupportedOperationException ignore) {
            /* throw a more specific exception? */
        }
    }

    /**
     * Check that an environment which is opened for replication can
     * also be opened as a standalone r/o environment.
     */
    public void testStandaloneRO()
        throws DatabaseException, IOException {

        RepEnvInfo[] repEnvInfo = initialOpenWithReplication();

        /* Try to re-open standalone r/o, should succeed */
        try {
            EnvironmentConfig reopenConfig = new EnvironmentConfig();
            reopenConfig.setTransactional(true);
            reopenConfig.setReadOnly(true);
            Environment env = new Environment(repEnvInfo[0].getEnvHome(),
                                              reopenConfig);
            env.close();
        } catch (DatabaseException e) {
            fail("Should be successful" + e);
        }
    }

    public void testStandaloneUtility()
        throws DatabaseException, IOException {

        RepEnvInfo[] repEnvInfo = initialOpenWithReplication();

        /* Try to re-open as a read/only utility, should succeed */
        try {
            EnvironmentConfig reopenConfig = new EnvironmentConfig();
            reopenConfig.setTransactional(true);
            reopenConfig.setReadOnly(true);
            EnvironmentImpl envImpl =
                CmdUtil.makeUtilityEnvironment(repEnvInfo[0].getEnvHome(),
                                           true /* readOnly */);
            envImpl.close();
        } catch (DatabaseException e) {
            fail("Should be successful" + e);
        }
    }

    private RepEnvInfo[] initialOpenWithReplication()
        throws DatabaseException, IOException {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);

        RepEnvInfo[] repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 2);
        RepTestUtils.joinGroup(repEnvInfo);
        for (RepEnvInfo repi : repEnvInfo) {
            repi.getEnv().close();
        }
        return repEnvInfo;
    }
}
