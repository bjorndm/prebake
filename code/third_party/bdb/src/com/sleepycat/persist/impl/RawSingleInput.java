/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RawSingleInput.java,v 1.10 2010/01/18 15:27:04 cwl Exp $
 */

package com.sleepycat.persist.impl;

import com.sleepycat.je.utilint.IdentityHashMap;

/**
 * Extends RawAbstractInput to convert array (ObjectArrayFormat and
 * PrimitiveArrayteKeyFormat) RawObject instances.
 *
 * @author Mark Hayes
 */
class RawSingleInput extends RawAbstractInput {

    private Object singleValue;
    private Format declaredFormat;

    RawSingleInput(Catalog catalog,
                   boolean rawAccess,
                   IdentityHashMap converted,
                   Object singleValue,
                   Format declaredFormat) {
        super(catalog, rawAccess, converted);
        this.singleValue = singleValue;
        this.declaredFormat = declaredFormat;
    }

    @Override
    Object readNext() {
        return checkAndConvert(singleValue, declaredFormat);
    }
}
