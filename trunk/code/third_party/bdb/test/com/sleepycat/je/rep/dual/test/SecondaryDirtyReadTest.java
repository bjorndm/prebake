/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SecondaryDirtyReadTest.java,v 1.8 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class SecondaryDirtyReadTest extends
        com.sleepycat.je.test.SecondaryDirtyReadTest {

    /* X'd out, pending Mark's changes to DPL vs internal txns in get(). */
    public static Test xsuite() {
        testClass = SecondaryDirtyReadTest.class;
        return com.sleepycat.je.test.SecondaryDirtyReadTest.suite();
    }
}
