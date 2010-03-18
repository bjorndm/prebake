/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DatabaseComparatorsTest.java,v 1.9 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual;

public class DatabaseComparatorsTest
    extends com.sleepycat.je.DatabaseComparatorsTest {

    /* Following test cases are setting non-transactional. */
    @Override
    public void testSR12517() {
    }

    @Override
    public void testDupsWithPartialComparatorNotAllowed() {
    }

    @Override
    public void testDatabaseCompareKeysArgs() 
        throws Exception {
    }

    @Override
    public void testSR16816DefaultComparator() 
        throws Exception {
    }

    @Override
    public void testSR16816ReverseComparator() 
        throws Exception {
    }
}
