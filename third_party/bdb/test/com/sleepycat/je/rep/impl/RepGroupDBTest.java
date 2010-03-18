/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepGroupDBTest.java,v 1.21 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.impl;

import java.util.Collection;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.ReplicaConsistencyPolicy;
import com.sleepycat.je.rep.NoConsistencyRequiredPolicy;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class RepGroupDBTest extends RepTestBase {

    public RepGroupDBTest() {
    }

    public void testBasic()
        throws DatabaseException, InterruptedException {

        RepTestUtils.joinGroup(repEnvInfo);
        RepTestUtils.syncGroupToLastCommit(repEnvInfo, repEnvInfo.length);
        verifyRepGroupDB(NoConsistencyRequiredPolicy.NO_CONSISTENCY);
    }

    /**
     * Verifies that the contents of the database matches the contents of the
     * individual repConfigs.
     *
     * @throws DatabaseException
     * @throws InterruptedException
     */
    private void verifyRepGroupDB(ReplicaConsistencyPolicy consistencyPolicy)
        throws DatabaseException, InterruptedException {
        /*
         * master and replica must all agree on the contents of the
         * rep group db and the local info about the node.
         */
        for (RepEnvInfo repi : repEnvInfo) {

            ReplicatedEnvironment rep = repi.getEnv();
            Collection<RepNodeImpl> nodes =
                RepGroupDB.getGroup(RepInternal.getRepImpl(rep),
                                    RepTestUtils.TEST_REP_GROUP_NAME,
                                    consistencyPolicy).
                                    getElectableNodes();
            assertEquals(repEnvInfo.length, nodes.size());
            for (RepNodeImpl n : nodes) {
                int nodeId = n.getNodeId();
                RepImpl repImpl =
                    RepInternal.getRepImpl(repEnvInfo[nodeId-1].getEnv());
                assertEquals(repImpl.getPort(), n.getPort());
                assertEquals(repImpl.getHostName(), n.getHostName());
                assertEquals(n.isQuorumAck(), true);
            }
        }
    }
}
