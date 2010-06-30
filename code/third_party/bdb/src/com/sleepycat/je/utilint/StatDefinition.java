/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: StatDefinition.java,v 1.6 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * Per-stat Metadata for JE statistics. The name and description are meant to
 * available in a verbose display of stats, and should be meaningful for users.
 */
public class StatDefinition implements Comparable {

    private final String name;
    private final String description;

    public StatDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name + ": " + description;
    }

    public int compareTo(Object other) {
        return toString().compareTo(other.toString());
    }
}
