/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RangeRestartException.java,v 1.12 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.utilint.InternalException;

/**
 * Thrown by the LockManager when requesting a RANGE_READ or RANGE_WRITE
 * lock, and a RANGE_INSERT lock is held or is waiting.  This exception is
 * caught by read operations and causes a restart of the operation.  It should
 * never be seen by the user.
 */
@SuppressWarnings("serial")
public class RangeRestartException extends InternalException {

    public RangeRestartException() {
        super();
    }
}
