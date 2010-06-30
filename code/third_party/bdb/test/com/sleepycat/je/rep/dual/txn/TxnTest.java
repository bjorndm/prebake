/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TxnTest.java,v 1.5 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.dual.txn;

public class TxnTest extends com.sleepycat.je.txn.TxnTest {

    @Override
    public void testBasicLocking()
        throws Throwable {
    }

    /* 
     * This test case is excluded because it uses the deprecated durability
     * API, which is prohibited in dual mode tests.
     */
    @Override
    public void testSyncCombo() 
        throws Throwable {
    }
}
