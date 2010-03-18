/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: VLSNFreezeLatchTest.java,v 1.6 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.elections;

import junit.framework.TestCase;

import com.sleepycat.je.rep.elections.Proposer.Proposal;
import com.sleepycat.je.rep.impl.node.CommitFreezeLatch;

public class VLSNFreezeLatchTest extends TestCase {

    private CommitFreezeLatch latch = new CommitFreezeLatch();
    /* A sequential series of proposals */
    private Proposal p1, p2, p3;

    @Override
    protected void setUp() throws Exception {
        latch = new CommitFreezeLatch();
        latch.setTimeOut(10 /* ms */);
        TimebasedProposalGenerator pg = new TimebasedProposalGenerator(1);
        p1 = pg.nextProposal();
        p2 = pg.nextProposal();
        p3 = pg.nextProposal();

        super.setUp();
    }

    public void testTimeout()
        throws InterruptedException {

        latch.freeze(p2);
        // Earlier event does not release waiters
        latch.vlsnEvent(p1);

        assertFalse(latch.awaitThaw());
        assertEquals(1, latch.getAwaitTimeoutCount());
    }

    public void testElection()
        throws InterruptedException {

        latch.freeze(p2);
        latch.vlsnEvent(p2);
        assertTrue(latch.awaitThaw());
        assertEquals(1, latch.getAwaitElectionCount());
    }

    public void testNewerElection()
        throws InterruptedException {

        latch.freeze(p2);
        latch.vlsnEvent(p3);
        assertTrue(latch.awaitThaw());
        assertEquals(1, latch.getAwaitElectionCount());
    }

    public void testNoFreeze()
        throws InterruptedException {

        latch.vlsnEvent(p1);

        assertFalse(latch.awaitThaw());
        assertEquals(0, latch.getAwaitTimeoutCount());
    }
}
