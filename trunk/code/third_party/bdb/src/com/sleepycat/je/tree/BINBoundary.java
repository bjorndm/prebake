/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: BINBoundary.java,v 1.10 2010/01/04 15:50:50 cwl Exp $:
 */

package com.sleepycat.je.tree;

/**
 * Contains information about the BIN returned by a search.
 */
public class BINBoundary {
    /** The last BIN was returned. */
    public boolean isLastBin;
    /** The first BIN was returned. */
    public boolean isFirstBin;
}
