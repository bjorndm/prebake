/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: WithRootLatched.java,v 1.19 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.DatabaseException;

public interface WithRootLatched {

    /**
     * doWork is called while the tree's root latch is held.
     */
    public IN doWork(ChildReference root)
        throws DatabaseException;
}
