/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: VLSNRecoveryProxy.java,v 1.2 2010/01/04 15:50:44 cwl Exp $
 */

package com.sleepycat.je.recovery;

import com.sleepycat.je.log.LogEntryHeader;
import com.sleepycat.je.log.entry.LogEntry;

/**
 * The VLSNRecoveryProxy is a handle for invoking VLSN tracking at recovery time.
 */
public interface VLSNRecoveryProxy {

    public void trackMapping(long lsn, 
                             LogEntryHeader currentEntryHeader,
                             LogEntry logEntry);
}
