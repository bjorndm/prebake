/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ElectionsTest.java,v 1.36 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.elections;

import static com.sleepycat.je.rep.elections.ProposerStatDefinition.PHASE1_NO_QUORUM;
import static com.sleepycat.je.rep.elections.ProposerStatDefinition.PROMISE_COUNT;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import junit.framework.TestCase;

import com.sleepycat.je.rep.QuorumPolicy;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.elections.Acceptor.SuggestionGenerator;
import com.sleepycat.je.rep.elections.Proposer.Proposal;
import com.sleepycat.je.rep.elections.Protocol.Value;
import com.sleepycat.je.rep.impl.RepGroupImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.ServiceDispatcher;

/**
 * Tests for elections as a whole.
 */
public class ElectionsTest extends TestCase {

    /* Number of nodes in the test */
    private static final int nodes = 3;
    private static final int monitors = 1;
    private int nretries;

    private final Object notificationsLock = new Object();
    private int listenerNotifications = 0;

    private final ReplicationConfig repConfig[] =
        new ReplicationConfig[nodes + 1];
    // private Monitor monitor;
    private boolean monitorInvoked = false;

    private final List<Elections> electionNodes = new LinkedList<Elections>();
    private MasterValue winningValue = null;

    /* Latch to ensure that required all listeners have made it through. */
    CountDownLatch listenerLatch;

    private RepGroupImpl repGroup = null;

    @Override
    public void setUp() throws IOException {
        repGroup = RepTestUtils.createTestRepGroup(nodes, monitors);
        for (RepNodeImpl rn : repGroup.getAllElectableMembers()) {
            ReplicationConfig config = new ReplicationConfig();
            repConfig[rn.getNodeId()] = config;
            config.setNodeName(rn.getName());
            config.setNodeHostPort(rn.getHostName()+ ":" +rn.getPort());
        }
    }

    @Override
    public void tearDown() throws Exception {
        if ((electionNodes != null) && (electionNodes.size() > 0)) {
            electionNodes.get(0).shutdownAcceptorsLearners
            (repGroup.getAcceptorSockets(), repGroup.getLearnerSockets());

            for (Elections node : electionNodes) {
                node.getServiceDispatcher().shutdown();
            }
        }
    }

    /**
     * Simulates the start up of the first "n" nodes. If < n nodes are started,
     * the others simulate being down.
     *
     * @param nstart nodes to start up
     * @param groupSize the size of the group
     * @throws IOException
     */
    public void startReplicationNodes(final int nstart,
                                      final int groupSize,
                                      final boolean testPriority)
        throws IOException {

        for (short nodeNum = 1; nodeNum <= nstart; nodeNum++) {
            Elections elections =
                new Elections(newRepNode(groupSize, nodeNum, testPriority),
                              new TestListener(),
                              newSuggestionGenerator(nodeNum, testPriority));
            elections.getRepNode().getServiceDispatcher().start();
            elections.startLearner();
            elections.participate();
            electionNodes.add(elections);
            elections.updateRepGroup(repGroup);
        }

        // Start up the Monitor as well.
        /*
        InetSocketAddress monitorSocket =
            repGroup.getMonitors().iterator().next().getLearnerSocket();
        monitor = new Monitor(repConfig[1].getGroupName(),
                              monitorSocket,
                              repGroup);
        monitor.setMonitorChangeListener(new MonitorChangeListener() {
            @Override
            public void replicationChange(MonitorChangeEvent monitorChangeEvent) {
                monitorInvoked = true;
                assertEquals(winningValue.getMasterNodeId(),
                        ((NewMasterEvent) monitorChangeEvent).getMasterId());
            }
        });
        monitor.startMonitor();
        */
    }

    public void startReplicationNodes(final int nstart,
                                      final int groupSize)
        throws IOException {
            startReplicationNodes(nstart, groupSize, false);
    }

    private RepNode newRepNode(final int groupSize,
                               final short nodeNum,
                               final boolean testPriority)
        throws IOException {

        final ServiceDispatcher serviceDispatcher =
            new ServiceDispatcher(repConfig[nodeNum].getNodeSocketAddress());

        return new RepNode(new NameIdPair(repConfig[nodeNum].getNodeName(),
                                          nodeNum),
                                          serviceDispatcher) {
            @Override
            public int getElectionQuorumSize(QuorumPolicy quorumPolicy) {

                return quorumPolicy.quorumSize(groupSize);
            }

            @Override
            public int getElectionPriority() {
                return testPriority ? (groupSize - nodeNum + 1) : 1;
            }

        };
    }

    private SuggestionGenerator newSuggestionGenerator(final short nodeNum,
                                                       final boolean testPriority) {
        return new Acceptor.SuggestionGenerator() {
            @SuppressWarnings("unused")
            public Value get(Proposal proposal) {
                return new MasterValue("testhost", 9999,
                                       new NameIdPair("n" + nodeNum,
                                                      nodeNum));
            }

            @SuppressWarnings("unused")
            public long getRanking(Proposal proposal) {
                return testPriority ? 1000l : nodeNum * 10l;
            }
        };
    }

    public void startReplicationNodes(int nstart)
        throws IOException {
        startReplicationNodes(nstart, nstart);
    }

    class TestListener implements Learner.Listener {

        @SuppressWarnings("unused")
        public void notify(Proposal proposal, Value value) {
            synchronized (notificationsLock) {
                listenerNotifications++;
            }
            assertEquals(winningValue, value);
            listenerLatch.countDown();
        }
    }

    private Elections setupAndRunElection(QuorumPolicy qpolicy,
                                          int activeNodes,
                                          int groupSize)
            throws IOException, InterruptedException {

        /* Start all of them. */
        startReplicationNodes(activeNodes, groupSize);
        winningValue = new MasterValue("testhost", 9999,
                                       new NameIdPair("n" + (activeNodes),
                                                      (activeNodes)));
        return runElection(qpolicy, activeNodes);
    }

    private Elections setupAndRunElection(int activeNodes) throws IOException,
            InterruptedException {
        return setupAndRunElection(QuorumPolicy.SIMPLE_MAJORITY,
                                   activeNodes,
                                   activeNodes);
    }

    private Elections setupAndRunElection(int activeNodes, int groupSize)
        throws IOException, InterruptedException {
        return setupAndRunElection(QuorumPolicy.SIMPLE_MAJORITY,
                                   activeNodes,
                                   groupSize);
    }

    private Elections runElection(QuorumPolicy qpolicy, int activeNodes)
            throws InterruptedException {
        listenerNotifications = 0;
        monitorInvoked = false;
        nretries = 2;
        listenerLatch = new CountDownLatch(activeNodes);
        /* Initiate an election on the first node. */
        Elections testElections = electionNodes.iterator().next();

        testElections.initiateElection(repGroup, qpolicy, nretries);
        /* Ensure that Proposer has finished. */
        testElections.waitForElection();
        return testElections;
    }

    private Elections runElection(int activeNodes)
        throws InterruptedException {

        return runElection(QuorumPolicy.SIMPLE_MAJORITY, activeNodes);
    }

    /**
     * Tests a basic election with everything being normal.
     */
    public void testBasicAllNodes()
        throws InterruptedException, IOException {

        /* Start all of them. */
        setupAndRunElection(nodes);
        listenerLatch.await();

        assertEquals(nodes, listenerNotifications);
        // assertTrue(monitorInvoked);
        runElection(nodes);
        listenerLatch.await();
        assertEquals(nodes, listenerNotifications);
        assertFalse(monitorInvoked);
    }

    public void testBasicAllPrioNodes()
        throws InterruptedException, IOException {

        /* Start all of them. */
        startReplicationNodes(nodes, nodes, true);
        winningValue = new MasterValue("testhost", 9999,
                                       new NameIdPair("n1", 1));
        runElection(QuorumPolicy.SIMPLE_MAJORITY, nodes);
        listenerLatch.await();

        assertEquals(nodes, listenerNotifications);
        // assertTrue(monitorInvoked);
        runElection(nodes);
        listenerLatch.await();
        assertEquals(nodes, listenerNotifications);
        assertFalse(monitorInvoked);
    }

    /**
     * Simulates one node never having come up.
     */
    public void testBasicAllButOneNode() throws InterruptedException,
            IOException {
        /*
         * Simulate one node down at startup, but sufficient nodes for a quorum.
         */
        setupAndRunElection(nodes - 1);
        listenerLatch.await();
        assertEquals(nodes - 1, listenerNotifications);
        // assertTrue(monitorInvoked);
    }

    /**
     * Tests a basic election with one node having crashed.
     */
    public void testBasicOneNodeCrash() throws InterruptedException,
            IOException {
        /* Start all of them. */
        Elections testElections = setupAndRunElection(nodes);
        listenerLatch.await();

        assertEquals(nodes, listenerNotifications);
        // assertTrue(monitorInvoked);
        assertEquals(nodes, testElections.getStats().getInt(PROMISE_COUNT));
        electionNodes.get(0).getAcceptor().shutdown();
        testElections = runElection(nodes);
        listenerLatch.await();
        /* The listener should have still obtained a notification. */
        assertEquals(nodes, listenerNotifications);
        /* Master unchanged so monitor not invoked */
        assertFalse(monitorInvoked);
        assertEquals(nodes - 1, testElections.getStats().getInt(PROMISE_COUNT));
    }

    /**
     * Tests a QuorumPolicy of ALL.
     */
    public void testQuorumPolicyAll() throws InterruptedException, IOException {

        /* Start all of them. */
        Elections testElections =
            setupAndRunElection(QuorumPolicy.ALL, nodes, nodes);
        listenerLatch.await();

        assertEquals(nodes, listenerNotifications);
        // assertTrue(monitorInvoked);
        assertEquals(nodes, testElections.getStats().getInt(PROMISE_COUNT));

        // Now remove one node and the elections should give up after
        // retries have expired.
        electionNodes.get(0).getAcceptor().shutdown();
        testElections = runElection(QuorumPolicy.ALL, nodes);

        assertEquals(0, listenerNotifications);
        assertFalse(monitorInvoked);

        /* Ensure that all retries were due to lack of a Quorum. */
        assertEquals
            (nretries, testElections.getStats().getInt(PHASE1_NO_QUORUM));
    }

    /**
     * Tests the case where a quorum could not be reached.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void testNoQuorum() throws IOException, InterruptedException {

        Elections testElections = setupAndRunElection(nodes/2, nodes);
        /*
         * No listeners were invoked so don't wait for a latch. No quorum,
         * therefore no listener invocations.
         */
        assertEquals(0, listenerNotifications);
        assertFalse(monitorInvoked);
        /* No listeners were invoked. */
        assertEquals(nodes / 2, listenerLatch.getCount());
        /* Ensure that all retries were due to lack of a Quorum. */
        assertEquals
            (nretries, testElections.getStats().getInt(PHASE1_NO_QUORUM));
    }
}
