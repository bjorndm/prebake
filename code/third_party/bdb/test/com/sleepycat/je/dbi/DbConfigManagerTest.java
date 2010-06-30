/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DbConfigManagerTest.java,v 1.34 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.dbi;

import junit.framework.TestCase;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class DbConfigManagerTest extends TestCase {

    /**
     * Test that parameter defaults work, that we can add and get
     * parameters
     */
    public void testBasicParams() {
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setCacheSize(2000);
        DbConfigManager configManager = new DbConfigManager(envConfig);

        /**
         * Longs: The config manager should return the value for an
         * explicitly set param and the default for one not set.
         *
         */
        assertEquals(2000,
                     configManager.getLong(EnvironmentParams.MAX_MEMORY));
        assertEquals(EnvironmentParams.ENV_RECOVERY.getDefault(),
                     configManager.get(EnvironmentParams.ENV_RECOVERY));
    }
}
