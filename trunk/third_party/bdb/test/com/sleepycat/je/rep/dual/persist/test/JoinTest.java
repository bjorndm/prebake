/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 */
package com.sleepycat.je.rep.dual.persist.test;

import junit.framework.Test;

public class JoinTest extends com.sleepycat.persist.test.JoinTest {

    public static Test suite() {
        testClass = JoinTest.class;
        return com.sleepycat.persist.test.JoinTest.suite();
    }

}
