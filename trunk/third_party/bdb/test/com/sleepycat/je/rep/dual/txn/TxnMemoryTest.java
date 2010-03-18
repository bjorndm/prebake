/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TxnMemoryTest.java,v 1.4 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.dual.txn;

import junit.framework.Test;

public class TxnMemoryTest extends com.sleepycat.je.txn.TxnMemoryTest {

    public static Test suite() {
        testClass = TxnMemoryTest.class;

        return com.sleepycat.je.txn.TxnMemoryTest.suite();
    }
}
