/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepGroupAdminTest.java,v 1.25 2010/01/04 15:51:04 cwl Exp $
 */
package com.sleepycat.je.rep;

import static com.sleepycat.je.rep.impl.RepParams.GROUP_NAME;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.rep.impl.RepGroupImpl;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.util.ReplicationGroupAdmin;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class RepGroupAdminTest extends RepTestBase {

    @Override
    protected void setUp()
        throws Exception {

        super.setUp();
    }

    @Override
    protected void tearDown()
        throws Exception {

        super.tearDown();
    }

    public void testRemoveMember() {
        createGroup(groupSize);
        ReplicatedEnvironment master = repEnvInfo[0].getEnv();
        assertTrue(master.getState().isMaster());

        RepEnvInfo rmMember = repEnvInfo[repEnvInfo.length-1];

        ReplicationGroupAdmin groupAdmin =
            new ReplicationGroupAdmin
            (RepTestUtils.TEST_REP_GROUP_NAME,
             rmMember.getRepImpl().getHelperSockets());
        assertEquals(groupSize,
                     master.getGroup().getElectableNodes().size());
        final String rmName = rmMember.
                                getRepNode().getNodeName();
        groupAdmin.removeMember(rmName);
        assertEquals(groupSize-1,
                     master.getGroup().getElectableNodes().size());
        rmMember.closeEnv();

        try {
            rmMember.openEnv();
            fail("Expected exception");
        } catch (EnvironmentFailureException e) {
            assertEquals(EnvironmentFailureReason.HANDSHAKE_ERROR,
                         e.getReason());
        }

        /* Exception tests.  We currently allow either IAE or EFE. */
        try {
            groupAdmin.removeMember("unknown node");
            fail("Expected exception");
        } catch (MemberNotFoundException e) {
            // Expected.
        }

        try {
            groupAdmin.removeMember(rmName);
            fail("Expected exception");
        } catch (MemberNotFoundException e) {
            // Expected.
        }

        try {
            groupAdmin.removeMember(master.getNodeName());
            fail("Expected exception");
        } catch (MasterStateException e) {
            // Expected.
        }
    }

    public void testAddMonitor()
        throws DatabaseException, InterruptedException {

        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);
        RepImpl lastImpl =
            RepInternal.getRepImpl(repEnvInfo[repEnvInfo.length-1].getEnv());

        Set<InetSocketAddress> helperSockets =
            new HashSet<InetSocketAddress>();
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            helperSockets.add(RepInternal.getRepImpl(rep).getSocket());
        }

        DbConfigManager lastConfigMgr = lastImpl.getConfigManager();
        ReplicationGroupAdmin groupAdmin =
            new ReplicationGroupAdmin(lastConfigMgr.get(GROUP_NAME),
                                        helperSockets);
        int lastId = lastImpl.getNodeId();
        final short monitorId = (short)(lastId+1);

        RepNodeImpl monitorNode =
            new RepNodeImpl(new NameIdPair("monitor" + monitorId,
                                           monitorId),
                                           NodeType.MONITOR,
                                           lastImpl.getHostName(),
                                           lastImpl.getPort()+1);
        groupAdmin.ensureMonitor(monitorNode);

        /* Second ensure should not result in errors. */
        groupAdmin.ensureMonitor(monitorNode);

        RepTestUtils.syncGroupToLastCommit(repEnvInfo, repEnvInfo.length);
        assertTrue(master.getState().isMaster());
        /* All nodes should know about the new monitor. */
        for (RepEnvInfo repi : repEnvInfo) {
            ReplicatedEnvironment rep = repi.getEnv();
            RepGroupImpl repGroup =
                RepInternal.getRepImpl(rep).getRepNode().getGroup();
            RepNodeImpl monitor = repGroup.getMember(monitorId);
            assertNotNull(monitor);
            assertTrue(monitorNode.equivalent(monitor));
        }

        /* Catch incorrect use of an existing non-monitor node name */
        RepNodeImpl badMonitorNode =
            new RepNodeImpl
                (new NameIdPair(repEnvInfo[1].getRepConfig().getNodeName()),
                 NodeType.MONITOR,
                 lastImpl.getHostName(),
                 lastImpl.getPort());
        try {
            groupAdmin.ensureMonitor(badMonitorNode);
            fail("expected exception");
        } catch (DatabaseException e) {
            assertTrue(true);
        }

        /* test exception from adding a non-monitor node. */
        badMonitorNode =
            new RepNodeImpl(new NameIdPair("monitor" + monitorId, monitorId),
                            NodeType.ELECTABLE,
                            lastImpl.getHostName(),
                            lastImpl.getPort());
        try {
            groupAdmin.ensureMonitor(badMonitorNode);
            fail("expected exception");
        } catch (EnvironmentFailureException e) {
            assertTrue(true);
        }
    }
}
