/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: VLSNProxy.java,v 1.6 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.entry.LogEntry;

/**
 * The VLSNProxy is a handle for invoking VLSN tracking at recovery time.
 */
public interface VLSNProxy {

    public void trackMapping(long lsn,
                             LogEntryHeader currentEntryHeader,
                             LogEntry targetLogEntry);
}
