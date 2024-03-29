/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LongMaxStat.java,v 1.2 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * A long stat which maintains a maximum value. It is initialized to 
 * Long.MIN_VALUE. The setMax() methods assigns the counter to 
 * MAX(counter, new value). 
 */
public class LongMaxStat extends LongStat {
    public LongMaxStat(StatGroup group, StatDefinition definition) {
        super(group, definition);
        clear();
    }

    public LongMaxStat(StatGroup group, 
                       StatDefinition definition, 
                       long counter) {
        super(group, definition);
        this.counter = counter;
    }

    @Override
    public void clear() {
        set(Long.MIN_VALUE);
    }

    /**
     * Set stat to MAX(current stat value, newValue).
     */
    public void setMax(long newValue) {
        counter = (counter < newValue) ? newValue : counter;
    }

    @Override
    String getFormattedValue() {
        if (counter == Long.MIN_VALUE) {
            return "NONE";
        }

        return Stat.FORMAT.format(counter);
    }
}

