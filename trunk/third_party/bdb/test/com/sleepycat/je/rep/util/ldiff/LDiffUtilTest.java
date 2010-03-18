/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: LDiffUtilTest.java,v 1.5 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util.ldiff;

import junit.framework.TestCase;

public class LDiffUtilTest extends TestCase {
    byte[][] al = new byte[][] { "key1|Value1".getBytes(),
            "key2|Value2".getBytes(), "key3|Value3".getBytes(),
            "key4|Value4".getBytes(), "key5|Value5".getBytes(),
            "key6|Value6".getBytes(), "key7|Value7".getBytes(),
            "key8|Value8".getBytes(), "key9|Value9".getBytes(),
            "key10|Value10".getBytes() };

    public void testPlaceHolder() {
        /* 
         * A Junit test will fail if there are no tests cases at all, so
         * here is a placeholder test.
         */
    }

    /* Verifies the basics of the rolling checksum computation. */
    /*
     * public void testgetRollingChksum() { List<byte[]> tlist =
     * Arrays.asList(al); int blockSize = 5; long rsum =
     * LDiffUtil.getRollingChksum(tlist.subList(0, blockSize)); for (int i = 1;
     * (i + blockSize) <= tlist.size(); i++) { int removeIndex = i - 1; int
     * addIndex = removeIndex + blockSize; List<byte[]> list =
     * tlist.subList(removeIndex + 1, addIndex + 1); // The reference value.
     * long ref = LDiffUtil.getRollingChksum(list); // The incrementally
     * computed chksum rsum = LDiffUtil.rollChecksum(rsum, blockSize,
     * LDiffUtil.getXi(al[removeIndex]), LDiffUtil.getXi(al[addIndex]));
     * assertEquals(ref, rsum); // System.err.printf("ref:%x, rsum:%x\n", ref,
     * rsum); } }
     */
}
