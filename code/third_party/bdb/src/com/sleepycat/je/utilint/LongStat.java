/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LongStat.java,v 1.7 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * A long JE stat.
 */
public class LongStat extends Stat<Long> {

    protected long counter;

    public LongStat(StatGroup group, StatDefinition definition) {
        super(group, definition);
    }

    public LongStat(StatGroup group, StatDefinition definition, long counter) {
        super(group, definition);
        this.counter = counter;
    }

    @Override
    public Long get() {
        return counter;
    }

    @Override
    public void set(Long newValue) {
        counter = newValue;
    }

    public void increment() {
        counter++;
    }

    public void add(long count) {
        counter += count;
    }

    @Override
    public void add(Stat<Long> other) {
        counter += other.get();
    }

    @Override
    public void clear() {
        counter = 0L;
    }

    @Override
    String getFormattedValue() {
        return Stat.FORMAT.format(counter);
    }
}
