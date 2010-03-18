/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NodeStateProtocolTest.java,v 1.2 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl;

import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.impl.TextProtocol.Message;
import com.sleepycat.je.rep.impl.node.NameIdPair;

/**
 * Tests the protocols used to querying the current state of a replica.
 */
public class NodeStateProtocolTest extends TextProtocolTestBase {

    private NodeStateProtocol protocol;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        protocol =
            new NodeStateProtocol(GROUP_NAME,
                                  new NameIdPair("n1", (short) 1),
                                  null);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Message[] createMessages() {
        Message[] messages = new Message[] {
            protocol.new NodeStateRequest(NODE_NAME),
            protocol.new NodeStateResponse(NODE_NAME, 
                                           NODE_NAME,
                                           System.currentTimeMillis(),
                                           State.MASTER) 
        };

        return messages;
    }

    @Override
    protected TextProtocol getProtocol() {
        return protocol;
    }
}
