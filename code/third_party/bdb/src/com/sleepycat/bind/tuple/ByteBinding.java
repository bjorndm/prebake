/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2010 Oracle.  All rights reserved.
 *
 * $Id: ByteBinding.java,v 1.17 2010/01/04 15:50:35 cwl Exp $
 */

package com.sleepycat.bind.tuple;

import com.sleepycat.je.DatabaseEntry;

/**
 * A concrete <code>TupleBinding</code> for a <code>Byte</code> primitive
 * wrapper or a <code>byte</code> primitive.
 *
 * <p>There are two ways to use this class:</p>
 * <ol>
 * <li>When using the {@link com.sleepycat.je} package directly, the static
 * methods in this class can be used to convert between primitive values and
 * {@link DatabaseEntry} objects.</li>
 * <li>When using the {@link com.sleepycat.collections} package, an instance of
 * this class can be used with any stored collection.  The easiest way to
 * obtain a binding instance is with the {@link
 * TupleBinding#getPrimitiveBinding} method.</li>
 * </ol>
 */
public class ByteBinding extends TupleBinding<Byte> {

    private static final int BYTE_SIZE = 1;

    // javadoc is inherited
    public Byte entryToObject(TupleInput input) {

        return input.readByte();
    }

    // javadoc is inherited
    public void objectToEntry(Byte object, TupleOutput output) {

        output.writeByte(object);
    }

    // javadoc is inherited
    protected TupleOutput getTupleOutput(Byte object) {

        return sizedOutput();
    }

    /**
     * Converts an entry buffer into a simple <code>byte</code> value.
     *
     * @param entry is the source entry buffer.
     *
     * @return the resulting value.
     */
    public static byte entryToByte(DatabaseEntry entry) {

        return entryToInput(entry).readByte();
    }

    /**
     * Converts a simple <code>byte</code> value into an entry buffer.
     *
     * @param val is the source value.
     *
     * @param entry is the destination entry buffer.
     */
    public static void byteToEntry(byte val, DatabaseEntry entry) {

        outputToEntry(sizedOutput().writeByte(val), entry);
    }

    /**
     * Returns a tuple output object of the exact size needed, to avoid
     * wasting space when a single primitive is output.
     */
    private static TupleOutput sizedOutput() {

        return new TupleOutput(new byte[BYTE_SIZE]);
    }
}
