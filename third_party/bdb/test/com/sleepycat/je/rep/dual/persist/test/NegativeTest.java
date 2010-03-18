/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NegativeTest.java,v 1.4 2010/01/04 15:51:04 cwl Exp $
 */
package com.sleepycat.je.rep.dual.persist.test;

import junit.framework.Test;

public class NegativeTest extends com.sleepycat.persist.test.NegativeTest {

    public static Test suite() {
        testClass = NegativeTest.class;
        return com.sleepycat.persist.test.NegativeTest.suite();
    }

}
