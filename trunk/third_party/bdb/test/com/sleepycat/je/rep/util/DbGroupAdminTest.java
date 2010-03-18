/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2010 Oracle.  All rights reserved.
 *
 * $Id: DbGroupAdminTest.java,v 1.3 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.rep.MasterStateException;
import com.sleepycat.je.rep.MemberNotFoundException;
import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.impl.RepGroupImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;

/*
 * A unit test which tests the DbGroupAdmin utility and also the utitlies  
 * provided by ReplicationGroupAdmin.
 */
public class DbGroupAdminTest extends TestCase {
    private final File envRoot;
    private RepEnvInfo[] repEnvInfo;

    public DbGroupAdminTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws Exception {

        RepTestUtils.removeRepEnvironments(envRoot);
        repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 3);
    }

    @Override
    public void tearDown() {
        RepTestUtils.shutdownRepEnvs(repEnvInfo);
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    /*
     * Test the removeMember behavior of DbGroupAdmin, since DbGroupAdmin
     * invokes ReplicationGroupAdmin, so it tests ReplicationGroupAdmin too.
     *
     * TODO: When the simple majority is configurable, need to test that a 
     * group becomes electable again when some nodes are removed.
     */
    public void testRemoveMember()
        throws Exception {

        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);

        /* Construct a DbGroupAdmin instance. */
        DbGroupAdmin dbAdmin = 
            new DbGroupAdmin(RepTestUtils.TEST_REP_GROUP_NAME, 
                             master.getRepConfig().getHelperSockets());

        /* Remove the master would throw MasterStateException. */
        try {
            dbAdmin.removeMember(master.getNodeName());
            fail("Shouldn't execute here, expect an exception.");
        } catch (MasterStateException e) {
            /* Expected exceptin. */
        }

        /* Remove an non-existed node would throw MemberNotFoundException. */
        try {
            dbAdmin.removeMember("Node 5");
            fail("Shouldn't execute here, expecte an exception.");
        } catch (MemberNotFoundException e) {
            /* Expected exception. */
        }

        /* Remove Node 3 from the group using removeMember API. */
        try {
            dbAdmin.removeMember(repEnvInfo[2].getEnv().getNodeName());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        RepGroupImpl groupImpl = master.getGroup().getRepGroupImpl();
        assertEquals(groupImpl.getAllElectableMembers().size(), 2);
       
        /* Remove Node 2 from the group using main method. */
        try {
            String[] args = new String[] {
                "-groupName", RepTestUtils.TEST_REP_GROUP_NAME, 
                "-helperHosts", master.getRepConfig().getNodeHostPort(),
                "-removeMember", repEnvInfo[1].getEnv().getNodeName() };
            DbGroupAdmin.main(args);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
        groupImpl = master.getGroup().getRepGroupImpl();
        assertEquals(groupImpl.getAllElectableMembers().size(), 1);
    }

    /* Test behaviors of ReplicationGroupAdmin. */
    public void testReplicationGroupAdmin() 
        throws Exception {

        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);

        ReplicationGroupAdmin groupAdmin = new ReplicationGroupAdmin
            (RepTestUtils.TEST_REP_GROUP_NAME,
             master.getRepConfig().getHelperSockets());
        
        /* Check the master name. */
        assertEquals(master.getNodeName(), groupAdmin.getMasterNodeName());

        /* Check the group information. */
        RepGroupImpl repGroupImpl = master.getGroup().getRepGroupImpl();
        assertEquals(repGroupImpl, groupAdmin.getGroup().getRepGroupImpl());

        /* Check the ensureMember utility, no monitors at the begining. */
        assertEquals(repGroupImpl.getMonitorNodes().size(), 0);

        ReplicationConfig monitorConfig = new ReplicationConfig();
        monitorConfig.setNodeName("Monitor 1");
        monitorConfig.setGroupName(RepTestUtils.TEST_REP_GROUP_NAME);
        monitorConfig.setNodeHostPort(RepTestUtils.TEST_HOST + ":" + "5004");
        monitorConfig.setHelperHosts(master.getRepConfig().getNodeHostPort());

        /* Add a new monitor. */
        RepNodeImpl monitorNode = 
            new RepNodeImpl(new NameIdPair(monitorConfig.getNodeName()),
                            NodeType.MONITOR,
                            monitorConfig.getNodeHostname(),
                            monitorConfig.getNodePort());
        groupAdmin.ensureMonitor(monitorNode);

        /* Check the group information and monitor after insertion. */
        repGroupImpl = master.getGroup().getRepGroupImpl();
        assertEquals(repGroupImpl, groupAdmin.getGroup().getRepGroupImpl());
        assertEquals(repGroupImpl.getMonitorNodes().size(), 1);
    }
}
