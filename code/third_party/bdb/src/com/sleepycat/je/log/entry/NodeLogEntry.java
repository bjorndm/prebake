/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NodeLogEntry.java,v 1.11 2010/01/04 15:50:44 cwl Exp $
 */

package com.sleepycat.je.log.entry;

import com.sleepycat.je.dbi.DatabaseId;

/**
 * Implemented by all LogEntry classes that provide a node ID.
 */
public interface NodeLogEntry extends LogEntry {

    /**
     * Returns the node ID.  This value is redundant with the main item (Node)
     * of a log entry.  It is returned separately so that it can be obtained
     * when the entry's main item (Node) is not loaded.  Partial loading is an
     * optimization for recovery.
     */
    long getNodeId();

    /**
     * All node entries have a database ID.
     */
    DatabaseId getDbId();
}
