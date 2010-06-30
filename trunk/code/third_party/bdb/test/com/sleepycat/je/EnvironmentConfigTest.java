/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentConfigTest.java,v 1.22 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class EnvironmentConfigTest extends TestCase {

    /**
     * Try out the validation in EnvironmentConfig.
     */
    public void testValidation() {

        /*
         * This validation should be successfull
         */
        Properties props = new Properties();
        props.setProperty(EnvironmentConfig.TXN_TIMEOUT, "10000");
        props.setProperty(EnvironmentConfig.TXN_DEADLOCK_STACK_TRACE, 
                          "true");
        new EnvironmentConfig(props); // Just instantiate a config object.

        /*
         * Should fail: we should throw because leftover.param is not
         * a valid parameter.
         */
        props.clear();
        props.setProperty("leftover.param", "foo");
        checkEnvironmentConfigValidation(props);

        /*
         * Should fail: we should throw because FileHandlerLimit
         * is less than its minimum
         */
        props.clear();
        props.setProperty(EnvironmentConfig.LOCK_N_LOCK_TABLES, "0");
        checkEnvironmentConfigValidation(props);

        /*
         * Should fail: we should throw because FileHandler.on is not
         * a valid value.
         */
        props.clear();
        props.setProperty(EnvironmentConfig.TXN_DEADLOCK_STACK_TRACE, "xxx");
        checkEnvironmentConfigValidation(props);
    }

    /**
     * Test single parameter setting.
     */
    public void testSingleParam()
        throws Exception {

        try {
            EnvironmentConfig config = new EnvironmentConfig();
            config.setConfigParam("foo", "7");
            fail("Should fail because of invalid param name");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        EnvironmentConfig config = new EnvironmentConfig();
        config.setConfigParam(EnvironmentParams.MAX_MEMORY_PERCENT.getName(),
                              "81");
        assertEquals(81, config.getCachePercent());
    }

    public void testInconsistentParams()
        throws Exception {

        try {
            EnvironmentConfig config = new EnvironmentConfig();
            config.setAllowCreate(true);
            config.setLocking(false);
            config.setTransactional(true);
            File envHome = new File(System.getProperty(TestUtils.DEST_DIR));
            new Environment(envHome, config);
            fail("Should fail because of inconsistent param values");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }

    /* Helper to catch expected exceptions. */
    private void checkEnvironmentConfigValidation(Properties props) {
        try {
            new EnvironmentConfig(props);
            fail("Should fail because of a parameter validation problem");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }
}
