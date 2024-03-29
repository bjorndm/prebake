/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: MonitorTest.java,v 1.27 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.monitor;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationGroup;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.impl.node.RepNode;

public class MonitorTest extends MonitorTestBase {

    /**
     * Test the direct API calls to get the master and group.
     */
    public void testMonitorDirect()
        throws DatabaseException, InterruptedException {

        repEnvInfo[0].openEnv();
        RepNode master =
            RepInternal.getRepImpl(repEnvInfo[0].getEnv()).getRepNode();
        assertTrue(master.isMaster());
        String masterNodeName = master.getNodeName();
        assertEquals(masterNodeName, monitor.getMasterNodeName());

        for (int i = 1; i < repEnvInfo.length; i++) {
            repEnvInfo[i].openEnv();
            Thread.sleep(1000);
            assertEquals(masterNodeName, monitor.getMasterNodeName());
            ReplicationGroup group = monitor.getGroup();
            assertEquals(RepInternal.getRepGroupImpl(group),
                         master.getGroup());
        }
        repEnvInfo[0].closeEnv();
        /* Wait for elections to settle down. */
        Thread.sleep(10000);
        assert(!masterNodeName.equals(monitor.getMasterNodeName()));
    }

    /**
     * Make sure code snippet in class javadoc compiles.
     * @throws IOException 
     * @throws DatabaseException 
     */
    public void testJavadoc() 
        throws DatabaseException, IOException {

        // Initialize the monitor node config
        try {
            ReplicationConfig monConfig = 
                new ReplicationConfig("PlanetaryRepGroup",       // groupName
                                      "mon1",                    // nodeName
                                      "monhost1.acme.com:7000"); // hostPort
            monConfig.setNodeType(NodeType.MONITOR);
            monConfig.setHelperHosts("mars.acme.com:5000,jupiter.acme.com:5000");

            Monitor monitor = new Monitor(monConfig);

            // If the monitor has not been registered as a member of the group,
            // register it now. register() returns the current node that is the
            // master.

            ReplicationNode currentMaster = monitor.register();

            // Start up the listener, so that it can be used to track changes
            // in the master node, or group composition.
            monitor.startListener(new MyChangeListener());
        } catch (IllegalArgumentException expected) {
        }
    }

    /* For javadoc and GSG */
    class MyChangeListener implements MonitorChangeListener {

        public void notify(NewMasterEvent newMasterEvent) {

            String newNodeName = newMasterEvent.getNodeName();

            InetSocketAddress newMasterAddr = 
                newMasterEvent.getSocketAddress();
            String newMasterHostName = newMasterAddr.getHostName();
            int newMasterPort = newMasterAddr.getPort();

            // Do something with this information here.
        }

        public void notify(GroupChangeEvent groupChangeEvent) {
            ReplicationGroup repGroup = groupChangeEvent.getRepGroup();
            
            // Do something with the new ReplicationGroup composition here.
        }

        public void notify(JoinGroupEvent joinGroupEvent) {
        }

        public void notify(LeaveGroupEvent leaveGroupEvent) {
        }
    } 
}
