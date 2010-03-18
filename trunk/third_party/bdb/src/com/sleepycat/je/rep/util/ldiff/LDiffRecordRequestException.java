/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LDiffRecordRequestException.java,v 1.2 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.rep.util.ldiff;

import com.sleepycat.je.OperationFailureException;

/**
 * Thrown when LDiff requesting records on remote database fails.
 */
public class LDiffRecordRequestException extends OperationFailureException {

    private static final long serialVersionUID = 1925430616L;

    /** 
     * For internal use only.
     * @hidden 
     */
    public LDiffRecordRequestException(String message) {
        super(null /*locker*/, false /*abortOnly*/, message, null /*cause*/);
    }

    private LDiffRecordRequestException(String message,
                                        LDiffRecordRequestException cause) {
        super(message, cause);
    }

    /** 
     * For internal use only.
     * @hidden 
     */
    @Override
    public OperationFailureException wrapSelf(String msg) {
        return new LDiffRecordRequestException(msg, this);
    }
}
