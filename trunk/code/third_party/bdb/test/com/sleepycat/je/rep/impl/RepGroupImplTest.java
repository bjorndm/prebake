/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepGroupImplTest.java,v 1.4 2010/01/04 15:51:05 cwl Exp $
 */
package com.sleepycat.je.rep.impl;

import java.net.UnknownHostException;

import junit.framework.TestCase;

import com.sleepycat.je.rep.utilint.RepTestUtils;

public class RepGroupImplTest extends TestCase {

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

    public void testSerializeDeserialize()
        throws UnknownHostException {

        int electablePeers = 5;
        int learners = 1;
        RepGroupImpl group = RepTestUtils.createTestRepGroup(5, 1);
        String s1 = group.serializeHex();
        String tokens[] = s1.split(TextProtocol.SEPARATOR_REGEXP);
        assertEquals(1 /* The Res group itself */ +
                     electablePeers + learners, /* the individual nodes. */
                     tokens.length);
        RepGroupImpl dgroup = RepGroupImpl.deserializeHex(tokens, 0);
        assertEquals(group, dgroup);
        String s2 = dgroup.serializeHex();
        assertEquals(s1,s2);
    }
}
