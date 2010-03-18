/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: MultiEnvTest.java,v 1.22 2010/01/04 15:51:03 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.util.TestUtils;

public class MultiEnvTest extends TestCase {

    private final File envHome1;
    private final File envHome2;

    public MultiEnvTest() {
        envHome1 = new File(System.getProperty(TestUtils.DEST_DIR));
        envHome2 = new File(System.getProperty(TestUtils.DEST_DIR),
                            "propTest");
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome1, false);
        TestUtils.removeLogFiles("Setup", envHome2, false);
    }

    @Override
    public void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome1, false);
        TestUtils.removeLogFiles("TearDown", envHome2, false);
    }

    public void testNodeIdsAfterRecovery() {

            /*
             * TODO: replace this test which previously checked that the node
             * id sequence shared among environments was correct with a test
             * that checks all sequences, including replicated ones. This
             * change is appropriate because the node id sequence is no longer
             * a static field.
             */
    }
}
