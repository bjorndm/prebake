/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SizeAwaitMapTest.java,v 1.5 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.rep.utilint;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

public class SizeAwaitMapTest extends TestCase {

    SizeAwaitMap<Integer, Integer> smap = null;
    SizeWaitThread testThreads[];
    AtomicInteger doneThreads;

    CountDownLatch startLatch = null;

    /* Large number to help expose concurrency issues, if any. */
    static final int threadCount = 200;

    protected void setUp() throws Exception {
        super.setUp();
        smap = new SizeAwaitMap<Integer,Integer>
        (Collections.synchronizedMap(new HashMap<Integer,Integer>()));
        testThreads = new SizeWaitThread[threadCount];
        doneThreads = new AtomicInteger(0);
        startLatch = new CountDownLatch(threadCount);
        for (int i=0; i < threadCount; i++) {
            testThreads[i] =
                new SizeWaitThread(i, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            testThreads[i].start();
        }
        // Wait for threads to start up
        startLatch.await();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void checkLiveThreads(int checkStart) {
        for (int j=checkStart; j < threadCount; j++) {
            assertTrue(testThreads[j].isAlive());
        }
        assertEquals(checkStart, doneThreads.intValue());
    }

    /**
     * Tests basic put/remove operations
     */
    public void testBasic() throws InterruptedException {
        testThreads[0].join();
        assertEquals(1, doneThreads.intValue());
        for (int i=1; i < threadCount; i++) {
            assertTrue(testThreads[i].isAlive());
            smap.put(i, i);
            testThreads[i].join();
            assertTrue(testThreads[i].success);
            // All subsequent threads continue to live
            checkLiveThreads(i+1);

            // Remove should have no impact
            smap.remove(i);
            checkLiveThreads(i+1);

            // Re-adding should have no impact
            smap.put(i, i);
            checkLiveThreads(i+1);
        }
    }

    /*
     * Tests clear operation.
     */
    public void testClear() throws InterruptedException {
        testThreads[0].join();
        assertEquals(1, doneThreads.intValue());
        /* Wait for the threads */
        while (smap.latchCount()!= (threadCount-1)) {
            Thread.sleep(10);
        }

        smap.clear();
        assertTrue(smap.size() == 0);
        for (int i=1; i < threadCount; i++) {
            testThreads[i].join();
            assertTrue(testThreads[i].interrupted);
        }
        assertEquals(threadCount, doneThreads.intValue());
    }

    /**
     * Threads which wait for specific map sizes.
     */
    private class SizeWaitThread extends Thread {

        /* The size to wait for. */
        final int size;
        final long timeout;
        final TimeUnit unit;
        boolean interrupted = false;
        boolean success = false;

        SizeWaitThread(int size, long timeout, TimeUnit unit) {
            this.size = size;
            this.timeout = timeout;
            this.unit = unit;
        }

        public void run() {
            startLatch.countDown();
            try {
                success = smap.sizeAwait(size, timeout, unit);
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                doneThreads.incrementAndGet();
            }

        }

    }

}
