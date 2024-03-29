/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: IntegralRateStat.java,v 1.3 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.utilint;

/**
 * A long stat which represents a rate whose value is Integral.
 */
public class IntegralRateStat extends LongStat {
    private final long factor;
    
    public IntegralRateStat(StatGroup group, 
                            StatDefinition definition, 
                            Stat<? extends Number> divisor, 
                            Stat<? extends Number> dividend,
                            long factor) {
        super(group, definition);
        this.factor = factor;

        calculateRate(divisor, dividend);
    }

    /* Calculate the rate based on the two stats. */
    private void calculateRate(Stat<? extends Number> divisor, 
                               Stat<? extends Number> dividend) {
        if (divisor == null || dividend == null) {
            counter = 0;
        } else {
            counter = (dividend.get().longValue() != 0) ?
                (divisor.get().longValue() * factor) / 
                 dividend.get().longValue() :
                 0;
        }
    }
}
