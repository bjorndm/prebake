/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EvolveStats.java,v 1.11 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * Statistics accumulated during eager entity evolution.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public class EvolveStats {

    private int nRead;
    private int nConverted;

    EvolveStats() {
    }

    void add(int nRead, int nConverted) {
        this.nRead += nRead;
        this.nConverted += nConverted;
    }

    /**
     * The total number of entities read during eager evolution.
     */
    public int getNRead() {
        return nRead;
    }

    /**
     * The total number of entities converted during eager evolution.
     */
    public int getNConverted() {
        return nConverted;
    }
}
