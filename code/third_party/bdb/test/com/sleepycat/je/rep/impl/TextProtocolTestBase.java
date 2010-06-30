/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TextProtocolTestBase.java,v 1.3 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl;

import junit.framework.TestCase;

import com.sleepycat.je.rep.impl.TextProtocol.InvalidMessageException;
import com.sleepycat.je.rep.impl.TextProtocol.Message;

/**
 * The superclass for all tests of protocols that inherit from TextProtocol.
 *
 * All subclasses need to create the messages belongs to each sub-protocol and 
 * return an instance of sub-protocol.
 */
public abstract class TextProtocolTestBase extends TestCase {

    private TextProtocol protocol;
    protected static final String GROUP_NAME = "TestGroup";
    protected static final String NODE_NAME = "Node 1";

    /**
     * Verify that all Protocol messages are idempotent under the 
     * serialization/de-serialization sequence.
     * @throws InvalidMessageException
     */
    public void testAllMessages()
        throws InvalidMessageException {

        Message[] messages = createMessages();

        protocol = getProtocol();

        /* Ensure that we are testing all of them */
        assertEquals(messages.length, protocol.messageCount());
        /* Now test them. */
        for (Message m : messages) {
            check(m);
            if (!getClass().equals(RepGroupProtocolTest.class) &&
                !getClass().equals(NodeStateProtocolTest.class)) {
                checkMismatch(m);
            }
        }
    }

    /* Create messages for test. */
    protected abstract Message[] createMessages();

    /* Return the concrete protocol. */
    protected abstract TextProtocol getProtocol();

    private void check(Message m1) 
        throws InvalidMessageException {

        String wireFormat = m1.wireFormat();
        Message m2 = protocol.parse(wireFormat);
        assertEquals(m1, m2);
    }

    /* Replaces a specific token vale with the one supplied. */
    private String hackToken(String wireFormat, 
                             TextProtocol.TOKENS tokenType,
                             String hackValue) {
        String[] tokens = wireFormat.split(TextProtocol.SEPARATOR_REGEXP);
        tokens[tokenType.ordinal()] = hackValue;
        String line = "";
        for (String token : tokens) {
            line += (token + TextProtocol.SEPARATOR);
        }

        return line.substring(0, line.length()-1);
    }

    /* Tests consistency checks on message headers. */
    private void checkMismatch(Message m1){
        String[] wireFormats = new String[] {
                hackToken(m1.wireFormat(), TextProtocol.TOKENS.VERSION_TOKEN,
                          "9999999"),
                hackToken(m1.wireFormat(), TextProtocol.TOKENS.NAME_TOKEN,
                          "BADGROUPNAME"),
                hackToken(m1.wireFormat(), TextProtocol.TOKENS.ID_TOKEN, 
                          "0") };

        for (String wireFormat : wireFormats) {
            try {
                protocol.parse(wireFormat);
                fail("Expected Illegal Arg Exception");
            } catch (InvalidMessageException e) {
                assertTrue(true);
            }
        }
    }
}
