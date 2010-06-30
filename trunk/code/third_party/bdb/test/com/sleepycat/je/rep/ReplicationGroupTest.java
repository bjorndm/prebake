/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ReplicationGroupTest.java,v 1.3 2009/07/22 04:58:22 tao Exp $
 */

package com.sleepycat.je.rep;

import java.util.Set;

import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.monitor.Monitor;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class ReplicationGroupTest extends RepTestBase {

    @SuppressWarnings("null")
    public void testBasic()
        throws InterruptedException {

        int electableNodeSize = groupSize-1;
        createGroup(electableNodeSize);
        repEnvInfo[groupSize-1].getRepConfig().setNodeType(NodeType.MONITOR);

        new Monitor(repEnvInfo[groupSize-1].getRepConfig()).register();

        for (int i=0; i < electableNodeSize; i++) {
            ReplicatedEnvironment env = repEnvInfo[i].getEnv();
            ReplicationGroup group = null;
            for (int j=0; j < 100; j++) {
                group = env.getGroup();
                if (group.getNodes().size() == groupSize) {
                    break;
                }
                /* Wait for the replica to catch up. */
                Thread.sleep(1000);
            }
            assertEquals(groupSize, group.getNodes().size());
            assertEquals(RepTestUtils.TEST_REP_GROUP_NAME, group.getName());

            for (RepEnvInfo rinfo : repEnvInfo) {
                final ReplicationConfig repConfig = rinfo.getRepConfig();
                ReplicationNode member =
                    group.getMember(repConfig.getNodeName());
                assertTrue(member != null);
                assertEquals(repConfig.getNodeName(), member.getName());
                assertEquals(repConfig.getNodeType(), member.getType());
                assertEquals(repConfig.getNodeSocketAddress(),
                             member.getSocketAddress());
            }

            Set<ReplicationNode> electableNodes = group.getElectableNodes();
            assertEquals(electableNodeSize, electableNodes.size());
            for (ReplicationNode n : electableNodes) {
                assertEquals(NodeType.ELECTABLE, n.getType());
            }

            final Set<ReplicationNode> monitorNodes = group.getMonitorNodes();
            for (ReplicationNode n : monitorNodes) {
                assertEquals(NodeType.MONITOR, n.getType());
            }
            assertEquals(1, monitorNodes.size());

            assertEquals(groupSize, group.getNodes().size());
        }
    }
}
