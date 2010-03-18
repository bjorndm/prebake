/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: InternalException.java,v 1.26 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;


/**
 * Some internal inconsistency exception.
 */
public class InternalException extends RuntimeException {

    private static final long serialVersionUID = 1584673689L;

    public InternalException() {
        super();
    }

    public InternalException(String message) {
        super(message);
    }
}
