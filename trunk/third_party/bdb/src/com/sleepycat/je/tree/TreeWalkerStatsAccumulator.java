/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TreeWalkerStatsAccumulator.java,v 1.10 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.tree;

/**
 * Accumulates stats about a tree during tree walking.
 */
public interface TreeWalkerStatsAccumulator {
    public void processIN(IN node, Long nid, int level);

    public void processBIN(BIN node, Long nid, int level);

    public void processDIN(DIN node, Long nid, int level);

    public void processDBIN(DBIN node, Long nid, int level);

    public void processDupCountLN(DupCountLN node, Long nid);

    public void incrementLNCount();

    public void incrementDeletedLNCount();
}
