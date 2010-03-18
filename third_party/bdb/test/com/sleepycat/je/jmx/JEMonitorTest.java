/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: JEMonitorTest.java,v 1.7 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.jmx;

import java.io.File;
import java.lang.reflect.Method;

import javax.management.Attribute;
import javax.management.DynamicMBean;
import javax.management.MBeanException;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;

/**
 * @excludeDualMode
 *
 * Instantiate and exercise the JEMonitor.
 */
public class JEMonitorTest extends TestCase {

    private static final boolean DEBUG = false;
    private File envHome;

    public JEMonitorTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws Exception {

        TestUtils.removeLogFiles("tearDown", envHome, true);
    }

    /**
     * Test JEMonitor's attributes getters. 
     */
    public void testGetters()
        throws Throwable {

        Environment env = null;
        try {
            env = openEnv(true);
            String environmentDir = env.getHome().getPath();
            DynamicMBean mbean = createMBean(env);
            MBeanTestUtils.validateGetters(mbean, 8, DEBUG); // see the change.
            env.close();

            /*
             * Replicated Environment must be transactional, so RepJEMonitor
             * can't open an Environment with non-transactional.
             */
            if (!this.getClass().getName().contains("rep")) {
                env = openEnv(false);
                mbean = createMBean(env);
                MBeanTestUtils.validateGetters(mbean, 6, DEBUG);
                env.close();
            }

            MBeanTestUtils.checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();
            if (env != null) {
                env.close();
            }
            
            throw t;
        }
    }

    /* 
     * Create a DynamicMBean with the assigned standalone or replicated 
     * Environment. 
     */
    protected DynamicMBean createMBean(Environment env) {
        return new JEMonitor(env);
    }

    /**
     * Test JEMonitor's attributes setters.
     */
    public void testSetters()
        throws Throwable {

        Environment env = null;
        try {
            /* Mimic an application by opening an environment. */
            env = openEnv(true);
            String environmentDir = env.getHome().getPath();

            /* Open an Mbean and set the Environment home. */
            DynamicMBean mbean = createMBean(env);

            /*
             * Try setting different attributes. Check against the
             * initial value, and the value after setting.
             */
            EnvironmentConfig config = env.getConfig();
            Class configClass = config.getClass();

            Method getCacheSize =
                configClass.getMethod("getCacheSize", (Class[]) null);
            checkAttribute(env,
                           mbean,
                           getCacheSize,
                           JEMBeanHelper.ATT_CACHE_SIZE,
                           new Long(100000)); // new value

            Method getCachePercent =
                configClass.getMethod("getCachePercent", (Class[]) null);
            checkAttribute(env,
                           mbean,
                           getCachePercent,
                           JEMBeanHelper.ATT_CACHE_PERCENT,
                           new Integer(10));
            env.close();

            MBeanTestUtils.checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();

            if (env != null) {
                env.close();
            }

            throw t;
        }
    }

    /**
     * Test correction of JEMonitor's operations invocation.
     */
    public void testOperations()
        throws Throwable {

        Environment env = null;
        try {
            env = openEnv(true);
            String environmentDir = env.getHome().getPath();
            DynamicMBean mbean = createMBean(env);
            int opNum = 0;
            if (!this.getClass().getName().contains("rep")) {
                opNum = 8;
            } else {
                opNum = 10;
            }
            MBeanTestUtils.validateMBeanOperations
                (mbean, opNum, true, null, null, DEBUG);

            /*
             * Getting database stats against a non-existing db ought to
             * throw an exception.
             */
            try {
                MBeanTestUtils.validateMBeanOperations
                    (mbean, opNum, true, "bozo", null, DEBUG);
                fail("Should not have run stats on a non-existent db");
            } catch (MBeanException expected) {
                // ignore
            }

            /*
             * Make sure the vanilla db open within the helper can open
             * a db created with a non-default configuration.
             */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            Database db = env.openDatabase(null, "bozo", dbConfig);

            /* insert a record. */
            DatabaseEntry entry = new DatabaseEntry();
            IntegerBinding.intToEntry(1, entry);
            db.put(null, entry, entry);

            MBeanTestUtils.validateMBeanOperations
                (mbean, opNum, true, "bozo", new String[] {"bozo"}, DEBUG);
            db.close();
            env.close();

            /*
             * Replicated Environment must be transactional, so can't test
             * RepJEMonitor with opening a non-transactional Environment.
             */
            if (!this.getClass().getName().contains("rep")) {
                env = openEnv(false);
                mbean = createMBean(env);
                MBeanTestUtils.validateMBeanOperations
                    (mbean, 7, true, null, null, DEBUG);
                env.close();
            }

            MBeanTestUtils.checkForNoOpenHandles(environmentDir);
        } catch (Throwable t) {
            t.printStackTrace();
            if (env != null) {
                env.close();
            }
            throw t;
        }
    }

    /* Check the correction of JEMonitor's attributes. */
    private void checkAttribute(Environment env,
                                DynamicMBean mbean,
                                Method configMethod,
                                String attributeName,
                                Object newValue)
        throws Exception {

        /* Check starting value. */
        EnvironmentConfig config = env.getConfig();
        Object result = configMethod.invoke(config, (Object[]) null);
        assertTrue(!result.toString().equals(newValue.toString()));

        /* set through mbean */
        mbean.setAttribute(new Attribute(attributeName, newValue));

        /* check present environment config. */
        config = env.getConfig();
        assertEquals(newValue.toString(),
                     configMethod.invoke(config, (Object[]) null).toString());

        /* check through mbean. */
        Object mbeanNewValue = mbean.getAttribute(attributeName);
        assertEquals(newValue.toString(), mbeanNewValue.toString());
    }

    /**
     * Checks that all parameters and return values are Serializable to
     * support JMX over RMI.
     */
    public void testSerializable()
        throws Exception {

        /* Create and close the environment. */
        Environment env = openEnv(true);

        /* Test without an open environment. */
        DynamicMBean mbean = createMBean(env);
        MBeanTestUtils.doTestSerializable(mbean);

        env.close();
    }

    /*
     * Helper to open an environment.
     */
    protected Environment openEnv(boolean openTransactionally)
        throws Exception {

        return MBeanTestUtils.openTxnalEnv(openTransactionally, envHome);
    }
}
