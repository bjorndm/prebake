/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ForeignKeyTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class ForeignKeyTest extends com.sleepycat.je.test.ForeignKeyTest {

    public static Test suite() {
        testClass = ForeignKeyTest.class;
        return com.sleepycat.je.test.ForeignKeyTest.suite();
    }
}
