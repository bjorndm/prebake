/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EvolveInternal.java,v 1.9 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * <!-- begin JE only -->
 * @hidden
 * <!-- end JE only -->
 * Internal access class that should not be used by applications.
 *
 * @author Mark Hayes
 */
public class EvolveInternal {

    /**
     * Internal access method that should not be used by applications.
     */
    public static EvolveEvent newEvent() {
        return new EvolveEvent();
    }

    /**
     * Internal access method that should not be used by applications.
     */
    public static void updateEvent(EvolveEvent event,
                                   String entityClassName,
                                   int nRead,
                                   int nConverted) {
        event.update(entityClassName);
        event.getStats().add(nRead, nConverted);
    }
}
