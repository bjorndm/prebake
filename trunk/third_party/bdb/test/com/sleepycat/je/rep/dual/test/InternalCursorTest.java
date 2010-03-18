/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: InternalCursorTest.java,v 1.3 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class InternalCursorTest
    extends com.sleepycat.je.test.InternalCursorTest {

    public static Test suite() {
        testClass = InternalCursorTest.class;
        return com.sleepycat.je.test.InternalCursorTest.suite();
    }
}
