/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DuplicateEntryException.java,v 1.4 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.dbi;

/**
 * Exception to indicate that an entry is already present in a node.
 */
@SuppressWarnings("serial")
class DuplicateEntryException extends RuntimeException {

    DuplicateEntryException() {
        super();
    }

    DuplicateEntryException(String message) {
        super(message);
    }
}
