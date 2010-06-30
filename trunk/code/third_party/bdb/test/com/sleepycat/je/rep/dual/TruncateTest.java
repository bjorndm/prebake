/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TruncateTest.java,v 1.7 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual;

public class TruncateTest extends com.sleepycat.je.TruncateTest {

    // TODO: relies on exact standalone LN counts. Rep introduces additional
    // LNs.
    @Override
    public void testEnvTruncateAbort() {
    }

    @Override
    public void testEnvTruncateCommit() {
    }

    @Override
    public void testEnvTruncateAutocommit() {
    }

    @Override
    public void testEnvTruncateNoFirstInsert() {
    }

    // Skip since it's non-transactional
    @Override
    public void testNoTxnEnvTruncateCommit() {
    }

    @Override
    public void testTruncateCommit() {
    }

    @Override
    public void testTruncateCommitAutoTxn() {
    }

    @Override
    public void testTruncateEmptyDeferredWriteDatabase() {
    }

    // TODO: Complex setup -- replicators not shutdown resulting in an
    // attempt to rebind to an already bound socket address
    @Override
    public void testTruncateAfterRecovery() {
    }

    /* Non-transactional access is not supported by HA. */
    @Override
    public void testTruncateNoLocking() {
    }
}
