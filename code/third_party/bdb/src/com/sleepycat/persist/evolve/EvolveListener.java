/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EvolveListener.java,v 1.12 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * The listener interface called during eager entity evolution.
 *
 * @see com.sleepycat.persist.evolve Class Evolution
 * @author Mark Hayes
 */
public interface EvolveListener {

    /**
     * The listener method called during eager entity evolution.
     *
     * @return true to continue evolution or false to stop.
     */
    boolean evolveProgress(EvolveEvent event);
}
