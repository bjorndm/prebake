/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ExceptionListenerTest.java,v 1.20 2010/01/04 15:51:10 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;

public class ExceptionListenerTest extends TestCase {

    private final File envHome;

    private volatile boolean exceptionThrownCalled = false;

    private DaemonThread dt = null;

    public ExceptionListenerTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    @Override
    public void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    public void testExceptionListener()
        throws Exception {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setExceptionListener(new MyExceptionListener());
        envConfig.setAllowCreate(true);
        Environment env = new Environment(envHome, envConfig);
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);

        assertSame(envConfig.getExceptionListener(),
                   envImpl.getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getCheckpointer().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getINCompressor().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getEvictor().getExceptionListener());

        dt = new MyDaemonThread(0, Environment.CLEANER_NAME, envImpl);
        dt.setExceptionListener(envImpl.getExceptionListener());
        dt.stifleExceptionChatter = true;
        dt.runOrPause(true);
        long startTime = System.currentTimeMillis();
        while (!dt.isShutdownRequested() &&
               System.currentTimeMillis() - startTime < 10 * 10000) {
            Thread.yield();
        }
        assertTrue("ExceptionListener apparently not called",
                   exceptionThrownCalled);

        /* Change the exception listener. */
        envConfig = env.getConfig();
        exceptionThrownCalled = false;
        envConfig.setExceptionListener(new MyExceptionListener());
        env.setMutableConfig(envConfig);

        assertSame(envConfig.getExceptionListener(),
                   envImpl.getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getCheckpointer().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getINCompressor().getExceptionListener());
        assertSame(envConfig.getExceptionListener(),
                   envImpl.getEvictor().getExceptionListener());

        dt = new MyDaemonThread(0, Environment.CLEANER_NAME, envImpl);
        dt.setExceptionListener(envImpl.getExceptionListener());
        dt.stifleExceptionChatter = true;
        dt.runOrPause(true);
        startTime = System.currentTimeMillis();
        while (!dt.isShutdownRequested() &&
               System.currentTimeMillis() - startTime < 10 * 10000) {
            Thread.yield();
        }
        assertTrue("ExceptionListener apparently not called",
                   exceptionThrownCalled);
    }

    private class MyDaemonThread extends DaemonThread {
        MyDaemonThread(long waitTime, String name, EnvironmentImpl envImpl) {
            super(waitTime, name, envImpl);
        }

        @Override
        protected void onWakeup() {
            throw new RuntimeException("test exception listener");
        }
    }

    private class MyExceptionListener implements ExceptionListener {
        public void exceptionThrown(ExceptionEvent event) {
            assertEquals("daemonName should be CLEANER_NAME",
                         Environment.CLEANER_NAME,
                         event.getThreadName());
            dt.requestShutdown();
            exceptionThrownCalled = true;
        }
    }
}
