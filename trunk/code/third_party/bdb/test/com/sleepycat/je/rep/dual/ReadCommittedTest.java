/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReadCommittedTest.java,v 1.9 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual;

public class ReadCommittedTest extends com.sleepycat.je.ReadCommittedTest {

    // TODO: Issue with API read lock under review
    @Override
    public void testWithTransactionConfig() {
    }

    // TODO: as above
    @Override
    public void testWithCursorConfig() {
    }

    // TODO: as above
    @Override
    public void testWithLockMode() {
    }
}
