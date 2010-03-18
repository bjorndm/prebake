/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DatabaseConfigTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual;

public class DatabaseConfigTest extends com.sleepycat.je.DatabaseConfigTest {

    /* Environment in this test case is set non-transactional. */
    @Override
    public void testConfig() {
    }

    /* Environment in this test case is set non-transactional. */
    @Override
    public void testConfigConfict() {
    }

    /* Database in this test case is set non-transactional. */
    @Override
    public void testIsTransactional()  {
    }

    /* Environment in this test case is set non-transactional. */
    @Override
    public void testExclusive()  {
    }

    /* Environment in this test case is set non-transactional. */
    @Override
    public void testConfigOverrideUpdateSR15743() {
    }
}
