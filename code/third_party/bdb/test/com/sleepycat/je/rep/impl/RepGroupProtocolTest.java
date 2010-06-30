/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepGroupProtocolTest.java,v 1.17 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl;

import java.net.UnknownHostException;

import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.impl.TextProtocol.Message;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.utilint.RepTestUtils;

/**
 * Tests the protocols used to maintain the rep group and support the Monitor.
 */
public class RepGroupProtocolTest extends TextProtocolTestBase {

    private RepGroupProtocol protocol;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        protocol =
            new RepGroupProtocol(GROUP_NAME,
                                 new NameIdPair("n1", (short) 1),
                                 null);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Message[] createMessages() {
        Message[] messages = null;
        try {
            messages = new Message[] {
                protocol.new EnsureNode
                (new RepNodeImpl(new NameIdPair(NODE_NAME, 1),
                                   NodeType.MONITOR,
                                   "localhost",
                                   5000)),
                protocol.new EnsureOK(new NameIdPair(NODE_NAME, 1)),
                protocol.new RemoveMember("m1"),
                protocol.new GroupRequest(),
                protocol.new GroupResponse
                    (RepTestUtils.createTestRepGroup(5,5)),
                protocol.new Fail
                    (RepGroupProtocol.FailReason.DEFAULT, "failed")
            };
        } catch (UnknownHostException e) {
            fail("Unexpected exception: " + e.getStackTrace());
        }

        return messages;
    }

    @Override
    protected TextProtocol getProtocol() {
        return protocol;
    }
}
