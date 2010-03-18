/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class PhantomRestartTest 
    extends com.sleepycat.je.test.PhantomRestartTest {

    public PhantomRestartTest(Spec spec, Boolean dups) {
        super(spec, dups);
    }

    public static Test suite() 
        throws Exception {

        testClass = PhantomRestartTest.class;
        return com.sleepycat.je.test.PhantomRestartTest.suite();
    }
}
