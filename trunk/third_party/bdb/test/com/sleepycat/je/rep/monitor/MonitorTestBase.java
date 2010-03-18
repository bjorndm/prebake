/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: MonitorTestBase.java,v 1.2 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.monitor;

import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.utilint.RepTestUtils;

public class MonitorTestBase extends RepTestBase {

    /* The monitor being tested. */
    protected Monitor monitor;

    @Override
    protected void setUp() 
        throws Exception {

        super.setUp();

        String helperHosts = repEnvInfo[0].getRepConfig().getNodeHostPort() + 
            "," + repEnvInfo[1].getRepConfig().getNodeHostPort();
        monitor = createMonitor(100, "mon10000", helperHosts);
    }

    @Override
    protected void tearDown()
        throws Exception {

        super.tearDown();
        monitor.shutdown();
    }

    private Monitor createMonitor(int portDelta,
                                  String monitorName,
                                  String nodeHosts)
        throws Exception {

        int monitorPort =
            Integer.parseInt(RepParams.DEFAULT_PORT.getDefault()) + portDelta;
        ReplicationConfig repConfig = new ReplicationConfig();
        repConfig.setGroupName(RepTestUtils.TEST_REP_GROUP_NAME);
        repConfig.setNodeName(monitorName);
        repConfig.setNodeType(NodeType.MONITOR);
        repConfig.setNodeHostPort(RepTestUtils.TEST_HOST + ":" + monitorPort);
        repConfig.setHelperHosts(nodeHosts);

        return new Monitor(repConfig);
    }
}
