/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: MemberNotFoundException.java,v 1.2 2010/01/04 15:50:45 cwl Exp $
 */

package com.sleepycat.je.rep;

import com.sleepycat.je.OperationFailureException;

/**
 * Thrown when an operation requires a replication group member and that member
 * is not present in the replication group.
 */
@SuppressWarnings("serial")
public class MemberNotFoundException extends OperationFailureException {

    /**
     * For internal use only.
     * @hidden
     */
    public MemberNotFoundException(String message) {
        super(null /*locker*/, false /*abortOnly*/, message, null /*cause*/);
    }

    /**
     * For internal use only.
     * @hidden
     */
    private MemberNotFoundException(String message,
                                      MemberNotFoundException cause) {
        super(message, cause);
    }

    /**
     * For internal use only.
     * @hidden
     */
    @Override
    public OperationFailureException wrapSelf(String msg) {
        return new MemberNotFoundException(msg, this);
    }
}
