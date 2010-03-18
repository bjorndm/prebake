/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: AtomicPutTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

import com.sleepycat.util.test.TxnTestCase;

public class AtomicPutTest extends com.sleepycat.je.test.AtomicPutTest {

    public static Test suite() {
        return txnTestSuite(AtomicPutTest.class, null,
                            //null);
                            new String[] {TxnTestCase.TXN_USER});
    }
}
