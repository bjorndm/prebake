/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: StringStat.java,v 1.2 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * A stat that saves a string; a way to say general information for later
 * display and access.
 */
public class StringStat extends Stat<String> {

    private String value;

    public StringStat(StatGroup group, 
                      StatDefinition definition) {
        super(group, definition);
    }

    public StringStat(StatGroup group, 
                      StatDefinition definition,
                      String initialValue) {
        super(group, definition);
        value = initialValue;
    }

    @Override
    public String get() {
        return value;
    }

    @Override
    public void set(String newValue) {
        value = newValue;
    }


    @Override
    public void add(Stat<String> otherStat) {
        value += otherStat.get();
    }

    @Override
    public void clear() {
        value = null;
    }

    @Override
    String getFormattedValue() {
        return value;
    }
}
