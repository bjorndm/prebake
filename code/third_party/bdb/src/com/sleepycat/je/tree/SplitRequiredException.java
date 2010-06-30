/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SplitRequiredException.java,v 1.13 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.tree;

/**
 * Indicates that we need to return to the top of the tree in order to
 * do a forced splitting pass.  A checked exception is used to ensure that it
 * is handled internally and not propagated through the API.
 */
@SuppressWarnings("serial")
class SplitRequiredException extends Exception {
    public SplitRequiredException() {
    }
}
