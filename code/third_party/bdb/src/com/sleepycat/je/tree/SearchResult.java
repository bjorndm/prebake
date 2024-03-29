/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SearchResult.java,v 1.21 2010/01/04 15:50:50 cwl Exp $:
 */

package com.sleepycat.je.tree;

/**
 * Contains the result of a tree search
 */
public class SearchResult {
    public boolean exactParentFound;
    public boolean keepSearching;

    /*
     * Set to true if a search stopped because a child was not resident, and
     * we are doing a do-not-fetch kind of search.
     */
    public boolean childNotResident;
    public IN parent;
    public int index;
        
    public SearchResult() {
        exactParentFound = false;
        keepSearching = true;
        parent = null;
        index = -1;
        childNotResident = false;
    }

    @Override
    public String toString() {
        return
            "exactParentFound="+ exactParentFound +
            " keepSearching=" + keepSearching +
            " parent=" + ((parent == null)? "null":
                          Long.toString(parent.getNodeId())) +
            " index=" + index +
            " childNotResident=" + childNotResident;
    }
}
