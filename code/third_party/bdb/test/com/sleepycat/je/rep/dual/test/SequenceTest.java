/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SequenceTest.java,v 1.7 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual.test;

import junit.framework.Test;

public class SequenceTest extends com.sleepycat.je.test.SequenceTest {

    public static Test suite() {
        testClass = SequenceTest.class;
        return com.sleepycat.je.test.SequenceTest.suite();
    }
}
