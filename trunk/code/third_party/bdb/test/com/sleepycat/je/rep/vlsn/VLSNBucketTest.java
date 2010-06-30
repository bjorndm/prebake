/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: VLSNBucketTest.java,v 1.10 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.rep.vlsn;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.VLSN;

/**
 * Low level test for basic VLSNBucket functionality
 * TODO: add more tests for buckets with non-populated offsets.
 */
public class VLSNBucketTest extends TestCase {

    private final boolean verbose = Boolean.getBoolean("verbose");

    public void testBasic() {
        int stride = 3;
        int maxMappings = 2;
        int maxDistance = 50;

        /*
         * Make a list of vlsn->lsns mappings for test data:
         * vlsn=1,lsn=3/10, 
         * vlsn=2,lsn=3/20, 
         *  ... etc ..
         */
        List<VLPair> vals = initData();
        VLSNBucket bucket = new VLSNBucket(3, // fileNumber, 
                                           stride, 
                                           maxMappings,
                                           maxDistance,
                                           vals.get(0).vlsn);

        /* Insert vlsn 1, 2 */
        assertTrue(bucket.empty());
        assertTrue(bucket.put(vals.get(0).vlsn, vals.get(0).lsn));
        assertFalse(bucket.empty());
        assertTrue(bucket.put(vals.get(1).vlsn, vals.get(1).lsn));
        
        /* 
         * Do some error checking - Make sure we can't put in a lsn for another
         * file. 
         */
        assertFalse(bucket.put(vals.get(2).vlsn, DbLsn.makeLsn(4, 20)));

        /* Make sure we can't put in a lsn that's too far away. */
        assertFalse(bucket.put(vals.get(2).vlsn, DbLsn.makeLsn(3, 100)));

        assertTrue(bucket.owns(vals.get(0).vlsn));
        assertTrue(bucket.owns(vals.get(1).vlsn));
        assertFalse(bucket.owns(vals.get(2).vlsn));

        assertTrue(bucket.put(vals.get(2).vlsn, vals.get(2).lsn));

        /* 
         * Check the mappings. There are three that were put in, and only
         * one that is stored. (1/10, (stored) 2/20, 3/30), plus the last lsn
         */
        assertEquals(vals.get(0).lsn,
                     bucket.getGTELsn(vals.get(0).vlsn));
        assertEquals(vals.get(2).lsn,
                     bucket.getGTELsn(vals.get(1).vlsn));
        assertEquals(vals.get(2).lsn,
                     bucket.getGTELsn(vals.get(2).vlsn));

        assertEquals(1, bucket.getNumOffsets());

        /* Fill the bucket up so there's more mappings. Add 4/40, 5/50, 6/60 */
        assertTrue(bucket.put(vals.get(3).vlsn, vals.get(3).lsn));
        assertTrue(bucket.put(vals.get(4).vlsn, vals.get(4).lsn));
        assertTrue(bucket.put(vals.get(5).vlsn, vals.get(5).lsn));

        /* 
         * Check that we reached the max mappings limit, and that this put is
         * refused.
         */
        assertFalse(bucket.put(new VLSN(7), DbLsn.makeLsn(3,70)));

        checkAccess(bucket, stride, vals);
    }

    /*
     * This bucket holds vlsn 1-6, just check all the access methods. */
    private void checkAccess(VLSNBucket bucket, 
                             int stride, 
                             List<VLPair> vals) {

        /* 
         * All the mappings should be there, and we should be able to retrieve
         * them.
         */
        for (int i = 0; i < vals.size(); i += stride) {
            VLPair pair = vals.get(i);
            assertTrue(bucket.owns(pair.vlsn));
            assertEquals(pair.lsn, bucket.getLsn(pair.vlsn));
        }

        /*
         *  With the strides, it's more work to use a loop to check GTE and LTE
         * than to just hard code the checks. If the expected array is grown,
         * add to these checks!
         */
        assertEquals(vals.get(0).lsn, 
                     bucket.getLTELsn(vals.get(0).vlsn));
        assertEquals(vals.get(0).lsn, 
                     bucket.getLTELsn(vals.get(1).vlsn));
        assertEquals(vals.get(0).lsn, 
                     bucket.getLTELsn(vals.get(2).vlsn));
        assertEquals(vals.get(3).lsn, 
                     bucket.getLTELsn(vals.get(3).vlsn));
        assertEquals(vals.get(3).lsn,
                     bucket.getLTELsn(vals.get(4).vlsn));
        assertEquals(vals.get(5).lsn, 
                     bucket.getLTELsn(vals.get(5).vlsn));

        assertEquals(vals.get(0).lsn,
                     bucket.getGTELsn(vals.get(0).vlsn));
        assertEquals(vals.get(3).lsn,
                     bucket.getGTELsn(vals.get(1).vlsn));
        assertEquals(vals.get(3).lsn, 
                     bucket.getGTELsn(vals.get(2).vlsn));
        assertEquals(vals.get(3).lsn,
                     bucket.getGTELsn(vals.get(3).vlsn));
        assertEquals(vals.get(5).lsn, 
                     bucket.getGTELsn(vals.get(4).vlsn));
        assertEquals(vals.get(5).lsn,
                     bucket.getGTELsn(vals.get(5).vlsn));
    }

    /**
     * Make a list of vlsn->lsns mappings for test data:
     * vlsn=1,lsn=3/10, 
     * vlsn=2,lsn=3/20, 
     *  ... etc ..
     */
    private List<VLPair> initData() {

        List<VLPair> vals = new ArrayList<VLPair>();
        for (int i = 1; i <= 6; i++) {
            vals.add(new VLPair(i, 3,  10 * i));
        }
        return vals;
    }

    public void testOutOfOrderPuts() {
        int stride = 3;
        int maxMappings = 2;
        int maxDistance = 50;

        List<VLPair> vals = initData();
        VLSNBucket bucket = new VLSNBucket(3, // fileNumber, 
                                           stride, 
                                           maxMappings,
                                           maxDistance,
                                           vals.get(0).vlsn);

        /* Insert vlsn 2, 1 */
        assertTrue(bucket.empty());
        assertTrue(bucket.put(vals.get(1).vlsn, vals.get(1).lsn));
        assertFalse(bucket.empty());

        assertTrue(bucket.owns(vals.get(1).vlsn));
        assertTrue(bucket.owns(vals.get(0).vlsn));
        assertFalse(bucket.owns(vals.get(2).vlsn));

        assertTrue(bucket.put(vals.get(0).vlsn, vals.get(0).lsn));

        /* 
         * Do some error checking - Make sure we can't put in a lsn for another
         * file. 
         */
        assertFalse(bucket.put(vals.get(2).vlsn, DbLsn.makeLsn(4, 20)));

        /* Make sure we can't put in a lsn that's too far away. */
        assertFalse(bucket.put(vals.get(2).vlsn, DbLsn.makeLsn(3, 100)));

        assertFalse(bucket.owns(vals.get(2).vlsn));

        /* 
         * Check the mappings. There are three that were put in, and only
         * one that is stored. (1/10, (stored) 2/20, 3/30)
         */
        assertEquals(1, bucket.getNumOffsets());

        /* 
         * Fill the bucket up so there's more mappings. Add 4/40, 5/50, 6/60 
         * out of order.
         */
        assertTrue(bucket.put(vals.get(4).vlsn, vals.get(4).lsn));
        assertTrue(bucket.put(vals.get(5).vlsn, vals.get(5).lsn));
        assertTrue(bucket.put(vals.get(2).vlsn, vals.get(2).lsn));
        assertTrue(bucket.put(vals.get(3).vlsn, vals.get(3).lsn));

        /* 
         * Check that we reached the max mappings limit, and that this put is
         * refused.
         */
        assertFalse(bucket.put(new VLSN(7), DbLsn.makeLsn(3,70)));

        checkAccess(bucket, stride, vals);
    }

    /*
     * Create a bucket with some out of order puts, so that there are empty
     * offsets, and make sure that the non-null gets succeed.
     */
    public void testGetNonNullWithHoles() {

        VLSNBucket bucket = new VLSNBucket(0,      // fileNumber, 
                                           2,      // stride, 
                                           20,     // maxMappings
                                           10000,  // maxDist
                                           new VLSN(1));
        assertTrue(bucket.put(new VLSN(1), 10));
        assertTrue(bucket.put(new VLSN(3), 30));
        /* 
         * Note that when we put in VLSN 6, the bucet's file offset array
         * will be smaller than it would normally be. It will only be
         * size=2. Do this to test the edge case of getNonNullLTELsn on 
         * a too-small array.
         */
        assertTrue(bucket.put(new VLSN(6), 60));

        assertEquals(10, bucket.getLTELsn(new VLSN(1)));
        assertEquals(10, bucket.getLTELsn(new VLSN(2)));
        assertEquals(30, bucket.getLTELsn(new VLSN(3)));
        assertEquals(30, bucket.getLTELsn(new VLSN(4)));
        assertEquals(30, bucket.getLTELsn(new VLSN(5)));
        assertEquals(60, bucket.getLTELsn(new VLSN(6)));

        assertEquals(10, bucket.getGTELsn(new VLSN(1)));
        assertEquals(30, bucket.getGTELsn(new VLSN(2)));
        assertEquals(30, bucket.getGTELsn(new VLSN(3)));
        assertEquals(60, bucket.getGTELsn(new VLSN(4)));
        assertEquals(60, bucket.getGTELsn(new VLSN(5)));
        assertEquals(60, bucket.getGTELsn(new VLSN(6)));

        assertEquals(10, bucket.getGTELsn(new VLSN(1)));
        assertEquals(30, bucket.getGTELsn(new VLSN(2)));
        assertEquals(30, bucket.getGTELsn(new VLSN(3)));
        assertEquals(60, bucket.getGTELsn(new VLSN(4)));
        assertEquals(60, bucket.getGTELsn(new VLSN(5)));
        assertEquals(60, bucket.getGTELsn(new VLSN(6)));
    }

    public void testRemoveFromTail() {
        int stride = 3;

        /* Create a set of test mappings. */
        List<VLPair> expected = new ArrayList<VLPair>();
        int start = 10;
        int end = 20;
        for (int i = start; i < end; i++) {
            expected.add(new VLPair( i, 0, i*10));
        }

        /*
         * Load a bucket with the expected mappings. Call removeFromTail()
         * at different points, and then check that all expected values remain.
         */
        for (int startDeleteVal = start-1; 
             startDeleteVal < end + 1; 
             startDeleteVal++) {

            VLSNBucket bucket = loadBucket(expected, stride);

            VLSN startDeleteVLSN = new VLSN(startDeleteVal);  
            if (verbose) {
                System.out.println("startDelete=" + startDeleteVal);
            }
            bucket.removeFromTail(startDeleteVLSN, 
                                  (startDeleteVal - 1) * 10); // prevLsn
            
            if (verbose) {
                System.out.println("bucket=" + bucket);
            }

            for (VLPair p : expected) {
                long lsn = DbLsn.NULL_LSN;
                if (bucket.owns(p.vlsn)) {
                    lsn = bucket.getLsn(p.vlsn);
                }

                if (p.vlsn.compareTo(startDeleteVLSN) >= 0) {
                    /* Anything >= startDeleteVLSN should be truncated. */
                    assertEquals("startDelete = " + startDeleteVLSN + 
                                 " p=" + p + " bucket=" + bucket, 
                                 DbLsn.NULL_LSN, lsn);
                } else {

                    if (((p.vlsn.getSequence() - start) % stride) == 0) {
                        /* 
                         * If is on a stride boundary, there should be a 
                         * mapping.
                         */
                        assertEquals("bucket=" + bucket +  " p= " + p,
                                     p.lsn, lsn);
                    } else if (p.vlsn.compareTo
                               (startDeleteVLSN.getPrev()) == 0) {
                        /* It's the last mapping. */
                        assertEquals(p.lsn, lsn);
                    } else {
                        assertEquals(DbLsn.NULL_LSN, lsn);
                    }
                }
            }
        }
    }

    private VLSNBucket loadBucket(List<VLPair> expected, int stride) {
        int maxMappings = 5;
        int maxDistance = 50;
        
        VLSNBucket bucket = new VLSNBucket(0, // fileNumber, 
                                           stride, 
                                           maxMappings,
                                           maxDistance,
                                           new VLSN(10));
        for (VLPair pair : expected) {
            assertTrue("pair = " + pair,
                       bucket.put(pair.vlsn, pair.lsn));
        }
        return bucket;
    }
}
