/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ProtocolTest.java,v 1.17 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.elections;

import java.util.Arrays;
import java.util.HashSet;

import com.sleepycat.je.rep.elections.Proposer.Proposal;
import com.sleepycat.je.rep.elections.Protocol.StringValue;
import com.sleepycat.je.rep.elections.Protocol.Value;
import com.sleepycat.je.rep.elections.Protocol.ValueParser;
import com.sleepycat.je.rep.impl.TextProtocol;
import com.sleepycat.je.rep.impl.TextProtocol.Message;
import com.sleepycat.je.rep.impl.TextProtocolTestBase;
import com.sleepycat.je.rep.impl.node.NameIdPair;

public class ProtocolTest extends TextProtocolTestBase {

    private Protocol protocol = null;

    @Override
    protected void setUp() {
        protocol = new Protocol(TimebasedProposalGenerator.getParser(),
                                new ValueParser() {
                                    public Value parse(String wireFormat) {
                                        if ("".equals(wireFormat)) {
                                            return null;
                                        }
                                        return new StringValue(wireFormat);

                                    }
                                },
                                GROUP_NAME,
                                new NameIdPair(NODE_NAME, 1),
                                null);
        protocol.updateNodeIds(new HashSet<Integer>
                               (Arrays.asList(new Integer(1))));
    }

    @Override
    protected void tearDown() {
        protocol = null;
    }

    @Override
    protected Message[] createMessages() {
        TimebasedProposalGenerator proposalGenerator =
            new TimebasedProposalGenerator();
        Proposal proposal = proposalGenerator.nextProposal();
        Value value = new Protocol.StringValue("test1");
        Value svalue = new Protocol.StringValue("test2");
        Message[] messages = new Message[] {
                protocol.new Propose(proposal),
                protocol.new Accept(proposal, value),
                protocol.new Result(proposal, value),
                protocol.new Shutdown(),
                protocol.new MasterQuery(),

                protocol.new Reject(proposal),
                protocol.new Promise(proposal, value, svalue, 100, 1),
                protocol.new Accepted(proposal, value),
                protocol.new MasterQueryResponse(proposal, value)
        };

        return messages;
    }

    @Override
    protected TextProtocol getProtocol() {
        return protocol;
    }
}
