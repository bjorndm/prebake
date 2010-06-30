/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ProtocolTest.java,v 1.2 2010/01/04 15:51:06 cwl Exp $
 */
package com.sleepycat.je.rep.monitor;

import java.util.Arrays;
import java.util.HashSet;

import com.sleepycat.je.rep.impl.RepGroupImpl;
import com.sleepycat.je.rep.impl.TextProtocol;
import com.sleepycat.je.rep.impl.TextProtocol.Message;
import com.sleepycat.je.rep.impl.TextProtocolTestBase;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.monitor.GroupChangeEvent.GroupChangeType;
import com.sleepycat.je.rep.monitor.LeaveGroupEvent.LeaveReason;

public class ProtocolTest extends TextProtocolTestBase {

    private Protocol protocol;

    @Override
    protected void setUp() {
        protocol = 
            new Protocol(GROUP_NAME, new NameIdPair(NODE_NAME, 1), null);
        protocol.updateNodeIds(new HashSet<Integer>
                               (Arrays.asList(new Integer(1))));
    }

    @Override
    protected void tearDown() {
        protocol = null;
    }

    @Override 
    protected Message[] createMessages() {
        Message[] messages = new Message [] {
                protocol.new GroupChange(new RepGroupImpl(GROUP_NAME), 
                                         NODE_NAME, GroupChangeType.ADD),
                protocol.new JoinGroup(NODE_NAME, 
                                       null, 
                                       System.currentTimeMillis()),
                protocol.new LeaveGroup(NODE_NAME, null, 
                                        LeaveReason.ABNORMAL_TERMINATION,  
                                        System.currentTimeMillis(),
                                        System.currentTimeMillis())
        };

        return messages;
    }

    @Override
    protected TextProtocol getProtocol() {
        return protocol;
    }
}
