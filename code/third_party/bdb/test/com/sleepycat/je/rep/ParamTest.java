/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ParamTest.java,v 1.25 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.util.TestUtils;

/**
 * Test setting and retrieving of replication configurations. Should test
 * - mutable configurations
 * - the static fields in ReplicatorParams most be loaded properly.
 *
 * TBW - test is incomplete.

 * Test setting and retrieving of replication params. Make sure we can
 * parse the special format of the je.rep.node.* param, and that we
 * give params specified in files precedence over params specified
 * programmatically.
 */
public class ParamTest extends TestCase {

    private final File envRoot;

    public ParamTest() {
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
     * THIS TESTCASE should go first in this file, before a replicator is
     * instantiated in this JVM, to ensure that an application can instantiate
     * a ReplicationConfig before instantiating a replicated environment.
     * ReplicationConfig needs a static from ReplicatorParams, and we have to
     * make sure it is loaded properly.
     */
    public void testConfigSetting() {
        ReplicationConfig repConfig = new ReplicationConfig();
        repConfig.setConfigParam("je.rep.groupName", "TestGroup");
    }

    /**
     * Make sure that this valid property can be set through both a file and
     * through a configuration instance.
     */
    private void verifySuccess(String paramName, String value) {
        try {
            Properties props = new Properties();
            props.put(paramName, value);
            DbConfigManager.validateProperties(props, false, null);
        } catch (Exception E) {
            E.printStackTrace();
            fail("Unexpected exception: " + E);
        }

        try {
            ReplicationConfig goodConfig = new ReplicationConfig();
            goodConfig.setConfigParam(paramName, value);
        } catch (Exception E) {
            E.printStackTrace();
            fail("Unexpected exception: " + E);
    }

        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setConfigParam(paramName, value);
            fail(paramName + " should have been rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Make sure that this invalid property will be caught when set through
     * either a file, or a configuration instance.
     */
    void verifyFailure(String paramName, String badValue) {
        try {
            Properties props = new Properties();
            props.put(paramName, badValue);
            DbConfigManager.validateProperties(props, false, null);
            fail("Bad value: " + badValue+ " not detected.");
        } catch (IllegalArgumentException expected) {
        }

        try {
            ReplicationConfig badConfig = new ReplicationConfig();
            badConfig.setConfigParam(paramName, badValue);
            fail("Bad value: " + badValue+ " not detected.");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testGroupName() {
        verifySuccess(RepParams.GROUP_NAME.getName(), "SleepycatGroup1");
        verifyFailure(RepParams.GROUP_NAME.getName(),
                      "Sleepycat Group 1");
    }

    public void testNodeHost() {
        verifySuccess(RepParams.NODE_HOST_PORT.getName(),
                      "localhost:5001");
        verifyFailure(RepParams.NODE_HOST_PORT.getName(),
                      "does.not.exist.com");
        /* Exists but is incorrect. */
        verifyFailure(RepParams.NODE_HOST_PORT.getName(), "mit.edu");
        verifyFailure(RepParams.NODE_HOST_PORT.getName(), "localhost:66000");
    }

    public void testHelper() {
        verifySuccess(RepParams.HELPER_HOSTS.getName(),
                      "localhost:5001");
        verifyFailure(RepParams.HELPER_HOSTS.getName(),
                      "does.not.exist.com");
        verifyFailure(RepParams.HELPER_HOSTS.getName(),
                      "localhost:66000");
    }
}
