/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: WaitForMasterListener.java,v 1.2 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.rep.utilint;

import java.util.concurrent.CountDownLatch;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class WaitForMasterListener implements StateChangeListener {
    CountDownLatch waitForMaster = new CountDownLatch(1);

    public void stateChange(StateChangeEvent stateChangeEvent) {
        if (stateChangeEvent.getState().equals
            (ReplicatedEnvironment.State.MASTER)) {
            waitForMaster.countDown();
        }
    }
    
    public void awaitMastership()
        throws InterruptedException {

        waitForMaster.await();
    }
}
