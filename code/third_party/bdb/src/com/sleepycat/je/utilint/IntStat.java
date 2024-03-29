/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: IntStat.java,v 1.7 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * An integer JE stat.
 */
public class IntStat extends Stat<Integer> {

    private int counter;

    public IntStat(StatGroup group, StatDefinition definition) {
        super(group, definition);
    }

    public IntStat(StatGroup group, StatDefinition definition, int counter) {
        super(group, definition);
        this.counter = counter;
    }

    @Override
    public Integer get() {
        return counter;
    }

    @Override
    public void set(Integer newValue) {
        counter = newValue;
    }

    public void increment() {
        counter++;
    }

    public void add(int count) {
        counter += count;
    }

    @Override
    public void add(Stat<Integer> otherStat) {
        counter += otherStat.get();
    }

    @Override
    public void clear() {
        counter = 0;
    }

    @Override
    String getFormattedValue() {
        return Stat.FORMAT.format(counter);
    }
}
