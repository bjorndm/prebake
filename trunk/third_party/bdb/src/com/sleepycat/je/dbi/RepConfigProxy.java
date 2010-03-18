/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepConfigProxy.java,v 1.6 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.ReplicaConsistencyPolicy;

/**
 * Used to pass a replication configuration instance through the non-HA code.
 */
public interface RepConfigProxy {
    public ReplicaConsistencyPolicy getConsistencyPolicy();
}
