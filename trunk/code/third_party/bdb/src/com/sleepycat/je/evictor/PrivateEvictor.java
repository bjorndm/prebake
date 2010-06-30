/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: PrivateEvictor.java,v 1.15 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.evictor;

import java.util.Iterator;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.utilint.StatGroup;

/**
 * The standard Evictor that operates on the INList for a single environment.
 * A single iterator over the INList is used to implement getNextIN.
 */
public class PrivateEvictor extends Evictor {

    private EnvironmentImpl envImpl;

    private Iterator<IN> scanIter;

    public PrivateEvictor(EnvironmentImpl envImpl, String name)
        throws DatabaseException {

        super(envImpl, name);
        this.envImpl = envImpl;
        scanIter = null;
    }

    @Override
    public StatGroup loadStats(StatsConfig config) {
        return super.loadStats(config);
    }

    @Override
    public void onWakeup()
        throws DatabaseException {

        if (!envImpl.isClosed()) {
            super.onWakeup();
        }
    }

    /**
     * Standard daemon method to set envImpl to null.
     */
    public void clearEnv() {
        envImpl = null;
    }

    /**
     * Do nothing.
     */
    public void noteINListChange(int nINs) {
    }

    /**
     * Only supported by SharedEvictor.
     */
    public void addEnvironment(EnvironmentImpl envImpl) {
        throw EnvironmentFailureException.unexpectedState();
    }

    /**
     * Only supported by SharedEvictor.
     */
    public void removeEnvironment(EnvironmentImpl envImpl) {
        throw EnvironmentFailureException.unexpectedState();
    }

    /**
     * Only supported by SharedEvictor.
     */
    public boolean checkEnv(EnvironmentImpl env) {
        throw EnvironmentFailureException.unexpectedState();
    }

    /**
     * Initializes the iterator, and performs special eviction once per batch.
     */
    long startBatch()
        throws DatabaseException {

        if (scanIter == null) {
            scanIter = envImpl.getInMemoryINs().iterator();
        }

        /* Perform special eviction without holding any latches. */
        return envImpl.specialEviction();
    }

    /**
     * Returns the simple INList size.
     */
    int getMaxINsPerBatch() {
        return envImpl.getInMemoryINs().getSize();
    }

    /**
     * Returns the next IN, wrapping if necessary.
     */
    IN getNextIN() {
        if (envImpl.getMemoryBudget().isTreeUsageAboveMinimum()) {
            if (!scanIter.hasNext()) {
                scanIter = envImpl.getInMemoryINs().iterator();
            }
            return scanIter.hasNext() ? scanIter.next() : null;
        } else {
            return null;
        }
    }

    /* For unit testing only. */
    Iterator<IN> getScanIterator() {
        return scanIter;
    }

    /* For unit testing only. */
    void setScanIterator(Iterator<IN> iter) {
        scanIter = iter;
    }
}
