/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RawField.java,v 1.13 2010/01/04 15:50:57 cwl Exp $
 */

package com.sleepycat.persist.raw;

/**
 * The definition of a field in a {@link RawType}.
 *
 * <p>{@code RawField} objects are thread-safe.  Multiple threads may safely
 * call the methods of a shared {@code RawField} object.</p>
 *
 * @author Mark Hayes
 */
public interface RawField {

    /**
     * Returns the name of the field.
     */
    String getName();

    /**
     * Returns the type of the field, without expanding parameterized types,
     * or null if the type is an interface type or the Object class.
     */
    RawType getType();
}
