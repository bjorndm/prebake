/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: JoinTest.java,v 1.8 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class JoinTest extends com.sleepycat.je.test.JoinTest {

    public static Test suite() {
        testClass = JoinTest.class;
        return com.sleepycat.je.test.JoinTest.suite();
    }
}
