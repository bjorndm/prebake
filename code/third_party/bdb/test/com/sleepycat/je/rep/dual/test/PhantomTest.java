/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class PhantomTest extends com.sleepycat.je.test.PhantomTest {

    public static Test suite() {
        testClass = PhantomTest.class;
        return com.sleepycat.je.test.PhantomTest.suite();
    }
}
