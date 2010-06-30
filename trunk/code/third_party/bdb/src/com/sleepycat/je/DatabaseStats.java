/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DatabaseStats.java,v 1.27 2010/01/04 15:50:36 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

/**
 * Statistics for a single database.
 */
public abstract class DatabaseStats implements Serializable {
    // no public constructor
    protected DatabaseStats() {}
}
