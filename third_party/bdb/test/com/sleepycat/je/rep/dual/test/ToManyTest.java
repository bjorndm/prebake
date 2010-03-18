/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ToManyTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class ToManyTest extends com.sleepycat.je.test.ToManyTest {

    public static Test suite() {
        testClass = ToManyTest.class;
        return com.sleepycat.je.test.ToManyTest.suite();
    }
}
