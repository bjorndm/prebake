/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TreeUtils.java,v 1.31 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.tree;

/**
 * Miscellaneous Tree utilities.
 */
public class TreeUtils {

    static private final String SPACES =
        "                                " +
        "                                " +
        "                                " +
        "                                ";

    /**
     * For tree dumper.
     */
    public static String indent(int nSpaces) {
        return SPACES.substring(0, nSpaces);
    }
}
