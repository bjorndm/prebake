/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EvolveEvent.java,v 1.8 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * The event passed to the EvolveListener interface during eager entity
 * evolution.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public class EvolveEvent {

    private EvolveStats stats;
    private String entityClassName;

    EvolveEvent() {
        this.stats = new EvolveStats();
    }

    void update(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    /**
     * The cummulative statistics gathered during eager evolution.
     */
    public EvolveStats getStats() {
        return stats;
    }

    /**
     * The class name of the current entity class being converted.
     */
    public String getEntityClassName() {
        return entityClassName;
    }
}
