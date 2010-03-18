/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: GroupServiceTest.java,v 1.19 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl;

import java.net.InetSocketAddress;
import java.util.Set;

import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.impl.RepGroupProtocol.EnsureOK;
import com.sleepycat.je.rep.impl.RepGroupProtocol.GroupResponse;
import com.sleepycat.je.rep.impl.TextProtocol.MessageExchange;
import com.sleepycat.je.rep.impl.TextProtocol.OK;
import com.sleepycat.je.rep.impl.TextProtocol.ResponseMessage;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.ServiceDispatcher;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class GroupServiceTest extends RepTestBase {

    @SuppressWarnings("null")
    public void testService() throws Exception {
        RepTestUtils.joinGroup(repEnvInfo);
        RepNode master = null;
        ServiceDispatcher masterDispatcher = null;
        /* Only the master supports the service. */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment replicator = repi.getEnv();
            RepNode repNode =
                RepInternal.getRepImpl(replicator).getRepNode();
            ServiceDispatcher dispatcher = repNode.getServiceDispatcher();
            assertEquals(repNode.isMaster(),
                         dispatcher.isRegistered(GroupService.SERVICE_NAME));
            if (repNode.isMaster()) {
                master = repNode;
                masterDispatcher = dispatcher;
            }
        }
        assertTrue(masterDispatcher != null);
        InetSocketAddress socketAddress = masterDispatcher.getSocketAddress();
        RepGroupProtocol protocol =
            new RepGroupProtocol(RepTestUtils.TEST_REP_GROUP_NAME,
                                 NameIdPair.NULL,
                                 master.getRepImpl());

        /* Test Group Request. */
        MessageExchange me =
            protocol.new MessageExchange(socketAddress,
                                         GroupService.SERVICE_NAME,
                                         protocol.new GroupRequest());
        me.run();
        ResponseMessage resp = me.getResponseMessage();
        assertEquals(GroupResponse.class, resp.getClass());
        assertEquals(master.getGroup(), ((GroupResponse)resp).getGroup());
        int monitorCount =
            ((GroupResponse)resp).getGroup().getMonitorNodes().size();

        /* Test add Monitor. */
        short monitorId = 1000;
        RepNodeImpl monitor =
            new RepNodeImpl(new NameIdPair("mon"+monitorId, monitorId),
                              NodeType.MONITOR, "localhost", 6000);
        me = protocol.new MessageExchange(socketAddress,
                                          GroupService.SERVICE_NAME,
                                          protocol.new EnsureNode(monitor));
        me.run();
        resp = me.getResponseMessage();
        assertEquals(EnsureOK.class, resp.getClass());


        /* Retrieve the group again, it should have the new monitor. */
        me = protocol.new MessageExchange(socketAddress,
                                          GroupService.SERVICE_NAME,
                                          protocol.new GroupRequest());
        me.run();
        resp = me.getResponseMessage();
        assertEquals(GroupResponse.class, resp.getClass());
        RepGroupImpl repGroup = ((GroupResponse)resp).getGroup();
        Set<RepNodeImpl> monitors = repGroup.getMonitorNodes();
        assertEquals(monitorCount+1, monitors.size());

        /* Exercise the remove member service to remove the monitor. */
        me = protocol.new MessageExchange
        (socketAddress,GroupService.SERVICE_NAME,
         protocol.new RemoveMember(monitor.getName()));
        me.run();
        resp = me.getResponseMessage();
        assertEquals(OK.class, resp.getClass());

        /* Retrieve the group again and check for the absence of the monitor */
        me = protocol.new MessageExchange(socketAddress,
                                          GroupService.SERVICE_NAME,
                                          protocol.new GroupRequest());
        me.run();
        resp = me.getResponseMessage();
        assertEquals(GroupResponse.class, resp.getClass());
        repGroup = ((GroupResponse)resp).getGroup();
        monitors = repGroup.getMonitorNodes();
        assertEquals(0, monitors.size());
    }
}
