/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReplicatorConfigTest.java,v 1.19 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.ReplicaConsistencyPolicy;
import com.sleepycat.je.rep.impl.PointConsistencyPolicy;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.utilint.VLSN;

public class ReplicatorConfigTest extends TestCase {

    ReplicationConfig repConfig;

    @Override
    protected void setUp()
        throws Exception {

        super.setUp();
        repConfig = new ReplicationConfig();
    }

    @Override
    protected void tearDown()
        throws Exception {

        super.tearDown();
    }

    // TODO: need tests for every entrypoint

    public void testConsistency() {

        ReplicaConsistencyPolicy policy =
            new TimeConsistencyPolicy(100, TimeUnit.MILLISECONDS,
                                      1, TimeUnit.SECONDS);
        repConfig.setConsistencyPolicy(policy);
        assertEquals(policy, repConfig.getConsistencyPolicy());

        policy = NoConsistencyRequiredPolicy.NO_CONSISTENCY;
        repConfig.setConsistencyPolicy(policy);
        assertEquals(policy, repConfig.getConsistencyPolicy());

        try {
            policy =
                new CommitPointConsistencyPolicy
                    (new CommitToken(new UUID(0, 0), 0), 0, null);
            repConfig.setConsistencyPolicy(policy);
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            policy =  new PointConsistencyPolicy(VLSN.NULL_VLSN);
            repConfig.setConsistencyPolicy(policy);
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            repConfig.setConfigParam
            (RepParams.CONSISTENCY_POLICY.getName(),
             "badPolicy");
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testHelperHosts() {
        /* Correct configs */
        repConfig.setHelperHosts("localhost");
        Set<InetSocketAddress> helperSockets = repConfig.getHelperSockets();
        assertEquals(1, helperSockets.size());
        assertEquals(Integer.parseInt(RepParams.DEFAULT_PORT.getDefault()),
                     helperSockets.iterator().next().getPort());

        repConfig.setHelperHosts("localhost:6000");
        helperSockets = repConfig.getHelperSockets();
        assertEquals(1, helperSockets.size());
        assertEquals(6000, helperSockets.iterator().next().getPort());

        repConfig.setHelperHosts("localhost:6000,localhost:6001");
        helperSockets = repConfig.getHelperSockets();
        assertEquals(2, helperSockets.size());

        /* Incorrect configs */
        /*
         * It would be nice if this were an effective test, but because various
         * ISPs will not actually let their DNS servers return an unknown
         * host, we can't rely on this failing.
        try {
            repConfig.setHelperHosts("unknownhost");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // Expected
        }
         */
        try {
            repConfig.setHelperHosts("localhost:80");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // Expected
        }
        try {
            repConfig.setHelperHosts("localhost:xyz");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // Expected
        }

        try {
            repConfig.setHelperHosts(":6000");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // Expected
        }
    }
}
