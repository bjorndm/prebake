/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: OperationTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */
package com.sleepycat.je.rep.dual.persist.test;

import junit.framework.Test;

public class OperationTest extends com.sleepycat.persist.test.OperationTest {

    public static Test suite() {
        testClass = OperationTest.class;
        return com.sleepycat.persist.test.OperationTest.suite();
    }
}
