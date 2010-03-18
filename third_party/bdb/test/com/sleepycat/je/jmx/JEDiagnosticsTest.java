/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: JEDiagnosticsTest.java,v 1.9 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.jmx;

import java.io.File;
import java.lang.reflect.Method;
import java.util.logging.Handler;
import java.util.logging.Level;

import javax.management.Attribute;
import javax.management.DynamicMBean;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.MemoryHandler;
import com.sleepycat.je.util.TestUtils;

/**
 * @excludeDualMode
 *
 * Instantiate and exercise the JEDiagnostics.
 */
public class JEDiagnosticsTest extends TestCase {

    private static final boolean DEBUG = false;
    private File envHome;

    public JEDiagnosticsTest() {
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
     * Test JEDiagnostics' attribute getters.
     */
    public void testGetters()
        throws Throwable {

        Environment env = null;
        try {
            if (!this.getClass().getName().contains("rep")) {
                env = openEnv(false);
                DynamicMBean mbean = createMBean(env);
                MBeanTestUtils.validateGetters(mbean, 3, DEBUG); 
                env.close();
            }

            env = openEnv(true);
            String environmentDir = env.getHome().getPath();
            DynamicMBean mbean = createMBean(env);
            MBeanTestUtils.validateGetters(mbean, 3, DEBUG);
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

    /* Create a DynamicMBean using a standalone or replicated Environment. */
    protected DynamicMBean createMBean(Environment env) {
        return new JEDiagnostics(env);
    }

    /**
     * Test JEDiagnostics' attribute setters.
     */
    public void testSetters()
        throws Throwable {

        Environment env = null;
        try {
            env = openEnv(true);
            String environmentDir = env.getHome().getPath();

            DynamicMBean mbean = createMBean(env);

            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            Class envImplClass = envImpl.getClass();

            /* Test setting ConsoleHandler's level. */
            Method getConsoleHandler =
                envImplClass.getMethod("getConsoleHandler", (Class[]) null);
            checkAttribute(env,
                           mbean,
                           getConsoleHandler,
                           "consoleHandlerLevel",
                           "OFF");

            /* Test setting FileHandler's level. */
            Method getFileHandler = 
                envImplClass.getMethod("getFileHandler", (Class[]) null);
            checkAttribute(env,
                           mbean,
                           getFileHandler,
                           "fileHandlerLevel",
                           "OFF");

            /* Test setting MemoryHandler's push level. */
            Method getMemoryHandler =
                envImplClass.getMethod("getMemoryHandler", (Class[]) null);
            checkAttribute(env,
                           mbean,
                           getMemoryHandler,
                           "memoryHandlerLevel",
                           "OFF");

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
     * Test JEDiagnostics' operations invocation.
     */
    public void testOperations() 
        throws Throwable {

        Environment env = null;
        try {
            env = openEnv(true);
            String environmentDir = env.getHome().getPath();
            DynamicMBean mbean = createMBean(env);

            /* 
             * RepJEDiagnostics has three operations while JEDiagnostics is
             * lack of getRepStats operation.
             */
            validateOperations(mbean, env, 2);
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

    /* Verify the correction of JEDiagnostics' operations. */
    private void validateOperations(DynamicMBean mbean,
                                    Environment env,
                                    int numExpectedOperations) 
        throws Throwable {

        MBeanTestUtils.checkOpNum(mbean, numExpectedOperations, DEBUG);
        
        MBeanInfo info = mbean.getMBeanInfo();
        MBeanOperationInfo[] ops = info.getOperations();
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        for (int i = 0; i < ops.length; i++) {
            String opName = ops[i].getName();
            if (opName.equals("resetLoggerLevel")) {

                /* 
                 * If this method is invoked by RepJEDiagnostics, the logger
                 * name should contain RepImpl, not EnvironmentImpl.
                 */
                Object[] params = new Object[] {"EnvironmentImpl", "OFF"};
                if (this.getClass().getName().contains("rep")) {
                    params = new Object[] {"RepImpl", "OFF"};
                }
                Object result = mbean.invoke
                    (opName, params,
                     new String[] {"java.lang.String", "java.lang.String"});
                assertEquals(envImpl.getLogger().getLevel(), Level.OFF);
                assertTrue(result == null);
            } else if (opName.equals("pushMemoryHandler")) {
                envImpl.getMemoryHandler().setLevel(Level.OFF);
                Object result = mbean.invoke(opName, null, null);
                assertTrue(result == null);
            } else {

                /* 
                 * Check the correction of the getRepStats operation that only
                 * in RepJEDiagnostics.
                 */
                if (this.getClass().getName().contains("rep")) {
                    Object result = mbean.invoke(opName, null, null);
                    assertTrue(result instanceof String);
                    MBeanTestUtils.checkObjectType
                        ("Operation", opName, ops[i].getReturnType(), result);
                }
            }
        }
    }
                                   
    /* Test this MBean's serialization. */
    public void testSerializable() 
        throws Throwable {

        Environment env = openEnv(true);
        
        DynamicMBean mbean = createMBean(env);
        MBeanTestUtils.doTestSerializable(mbean);

        env.close();
    }
    
    /* Check this MBean's attributes. */ 
    private void checkAttribute(Environment env,
                                DynamicMBean mbean,
                                Method getMethod,
                                String attributeName,
                                Object newValue)
        throws Exception {

        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        Object result = getMethod.invoke(envImpl, (Object[]) null);
        assertTrue(!result.toString().equals(newValue.toString()));

        mbean.setAttribute(new Attribute(attributeName, newValue));

        envImpl = DbInternal.getEnvironmentImpl(env);
        Handler handler = (Handler) getMethod.invoke(envImpl, (Object[]) null);
        if (!"memoryHandlerLevel".equals(attributeName)) {
            assertEquals(newValue.toString(), handler.getLevel().toString());
        } else {
            assertEquals(newValue.toString(), 
                         ((MemoryHandler) handler).getPushLevel().toString());
        }

        Object mbeanNewValue = mbean.getAttribute(attributeName);
        assertEquals(newValue.toString(), mbeanNewValue.toString());
    }

    /*
     * Helper to open an environment.
     */
    protected Environment openEnv(boolean enableFileHandler)
        throws Exception {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);

        return new Environment(envHome, envConfig);
    }
}
