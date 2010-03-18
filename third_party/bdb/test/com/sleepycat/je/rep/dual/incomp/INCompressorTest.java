/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: INCompressorTest.java,v 1.7 2010/01/04 15:51:04 cwl Exp $
 */
package com.sleepycat.je.rep.dual.incomp;


public class INCompressorTest 
    extends com.sleepycat.je.incomp.INCompressorTest {

    /* The following test cases are non-transactional. */
    @Override
    public void testDeleteTransactional() {
    }

    @Override
    public void testDeleteNonTransactional() {
    }

    @Override
    public void testDeleteDuplicate() {
    }

    @Override
    public void testRemoveEmptyBIN() {
    }

    @Override
    public void testRemoveEmptyDBIN() {
    }

    @Override
    public void testRemoveEmptyDBINandBIN() {
    }

    @Override
    public void testRollForwardDelete() {
    }

    @Override
    public void testRollForwardDeleteDuplicate() {
    }

    @Override
    public void testLazyPruning() {
    }

    @Override
    public void testLazyPruningDups() {
    }

    @Override
    public void testEmptyInitialDBINScan() {
    }

    @Override
    public void testEmptyInitialBINScan() {
    }

    @Override
    public void testNodeNotEmpty() {
    }

    @Override
    public void testAbortInsert() {
    }

    @Override
    public void testAbortInsertDuplicate() {
    }

    @Override
    public void testRollBackInsert() {
    }

    @Override
    public void testRollBackInsertDuplicate() {
    }
}
