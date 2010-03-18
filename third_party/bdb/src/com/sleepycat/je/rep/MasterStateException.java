/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: MasterStateException.java,v 1.2 2010/01/04 15:50:45 cwl Exp $
 */
package com.sleepycat.je.rep;


/**
 * This exception indicates that the application attempted an operation that is
 * not permitted when it is in the <code>Replicator.State.Master state</code>.
 */
@SuppressWarnings("serial")
public class MasterStateException extends StateChangeException {

    /**
     * For internal use only.
     * @hidden
     */
    public MasterStateException(StateChangeEvent stateChangeEvent) {
        super(null, stateChangeEvent);
    }

    /**
     * For internal use only.
     * @hidden
     */
    public MasterStateException(String message) {
        super(message, null);
    }

    private MasterStateException(String message,
                                 MasterStateException cause) {
        super(message, cause);
    }

    /**
     * For internal use only.
     * @hidden
     */
    @Override
    public MasterStateException wrapSelf(String msg) {
        return new MasterStateException(msg, this);
    }
}
