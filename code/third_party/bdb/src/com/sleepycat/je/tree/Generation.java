/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: Generation.java,v 1.18 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.tree;

public final class Generation {
    static private long nextGeneration = 0;

    static long getNextGeneration() {
        return nextGeneration++;
    }
}
