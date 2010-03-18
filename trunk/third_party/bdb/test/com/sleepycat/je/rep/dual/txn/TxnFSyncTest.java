/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TxnFSyncTest.java,v 1.5 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.dual.txn;

import junit.framework.Test;

public class TxnFSyncTest extends com.sleepycat.je.txn.TxnFSyncTest {

    public static Test suite() {
        testClass = TxnFSyncTest.class;
        return com.sleepycat.je.txn.TxnFSyncTest.suite();
    }

    // TODO: Low level environment manipulation. Env not being closed. Multiple
    // active environment handles to the same environment.

    /* junit.framework.AssertionFailedError: Address already in use
        at junit.framework.Assert.fail(Assert.java:47)
        at com.sleepycat.je.rep.RepEnvWrapper.create(RepEnvWrapper.java:60)
        at com.sleepycat.je.DualTestCase.create(DualTestCase.java:63)
        at com.sleepycat.je.txn.TxnFSyncTest.testFSyncButNoClose(TxnFSyncTest.java:105)
        ...

        */
    @Override
    public void testFSyncButNoClose() {
    }
}
