/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: ServiceDispatcherTestBase.java,v 1.5 2010/01/04 15:51:06 cwl Exp $
 */
package com.sleepycat.je.rep.util;

import java.net.InetSocketAddress;

import junit.framework.TestCase;

import com.sleepycat.je.rep.utilint.ServiceDispatcher;

public abstract class ServiceDispatcherTestBase extends TestCase {

    protected ServiceDispatcher dispatcher = null;
    private static final int TEST_PORT = 5000;
    protected InetSocketAddress dispatcherAddress;

    public ServiceDispatcherTestBase() {
        super();
    }

    public ServiceDispatcherTestBase(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dispatcherAddress = new InetSocketAddress("localhost", TEST_PORT);
        dispatcher = new ServiceDispatcher(dispatcherAddress);
        dispatcher.start();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        dispatcher.shutdown();
        dispatcher = null;
    }
}
